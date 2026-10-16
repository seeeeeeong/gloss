package gloss.domain.llm.domain

import gloss.domain.llm.domain.ToolUseDecision.Problem
import gloss.domain.llm.domain.ToolUsePolicy.Turn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolUsePolicyTest {
    private val schema = """{"type": "object", "properties": {"query": {"type": "string"}}}"""
    private val question = Turn(Turn.Kind.OTHER)

    private fun decide(
        names: List<String> = listOf("search_web", "fetch-page"),
        forced: ToolUsePolicy.Forced? = null,
        thinking: Boolean = false,
        schemas: List<String> = names.map { schema },
    ) = ToolUsePolicy.decide(
        offer = ToolUsePolicy.Offer(names = names, schemas = schemas, forced = forced, thinking = thinking),
        turns = listOf(question),
    )

    private fun history(
        vararg turns: Turn,
        thinking: Boolean = false,
    ) = ToolUsePolicy.decide(
        offer =
            ToolUsePolicy.Offer(
                names = listOf("search_web"),
                schemas = listOf(schema),
                forced = null,
                thinking = thinking,
            ),
        turns = listOf(question, *turns),
    )

    private fun calls(
        vararg ids: String,
        reasoned: Boolean = false,
    ) = Turn(kind = Turn.Kind.ASSISTANT, callIds = ids.toList(), reasoned = reasoned)

    private fun answer(id: String) = Turn(kind = Turn.Kind.TOOL, answers = id)

    @Test
    fun `tools the model may call or not are accepted, thinking or not`() {
        assertThat(decide()).isEqualTo(ToolUseDecision.Accepted)
        assertThat(decide(thinking = true)).isEqualTo(ToolUseDecision.Accepted)
        assertThat(decide(names = emptyList())).isEqualTo(ToolUseDecision.Accepted)
    }

    @Test
    fun `forcing a tool, any or a named one, is accepted with thinking off`() {
        assertThat(decide(forced = ToolUsePolicy.Forced(name = null))).isEqualTo(ToolUseDecision.Accepted)
        assertThat(decide(forced = ToolUsePolicy.Forced("search_web"))).isEqualTo(ToolUseDecision.Accepted)
    }

    @Test
    fun `forcing a tool with thinking on is refused, as the provider would, rather than thinking turned off`() {
        assertThat(decide(forced = ToolUsePolicy.Forced(name = null), thinking = true))
            .isEqualTo(ToolUseDecision.Refused(Problem.FORCED_WHILE_THINKING))
        assertThat(decide(forced = ToolUsePolicy.Forced("search_web"), thinking = true))
            .isEqualTo(ToolUseDecision.Refused(Problem.FORCED_WHILE_THINKING))
    }

    @Test
    fun `forcing a tool that is not offered, or with none offered, is refused`() {
        assertThat(decide(forced = ToolUsePolicy.Forced("submit_toc")))
            .isEqualTo(ToolUseDecision.Refused(Problem.FORCED_TOOL_NOT_OFFERED))
        assertThat(decide(names = emptyList(), forced = ToolUsePolicy.Forced(name = null)))
            .isEqualTo(ToolUseDecision.Refused(Problem.NOTHING_TO_FORCE))
    }

    @Test
    fun `a name the provider would refuse, or one offered twice, is refused`() {
        assertThat(decide(names = listOf("search web"))).isEqualTo(ToolUseDecision.Refused(Problem.BAD_NAME))
        assertThat(decide(names = listOf(""))).isEqualTo(ToolUseDecision.Refused(Problem.BAD_NAME))
        assertThat(decide(names = listOf("a".repeat(129)))).isEqualTo(ToolUseDecision.Refused(Problem.BAD_NAME))
        assertThat(decide(names = listOf("a".repeat(128)))).isEqualTo(ToolUseDecision.Accepted)
        assertThat(decide(names = listOf("find_books", "find_books")))
            .isEqualTo(ToolUseDecision.Refused(Problem.DUPLICATE_NAME))
    }

    @Test
    fun `up to 128 tools are offered in one call, and no more`() {
        assertThat(decide(names = (1..128).map { "tool_$it" })).isEqualTo(ToolUseDecision.Accepted)
        assertThat(decide(names = (1..129).map { "tool_$it" }))
            .isEqualTo(ToolUseDecision.Refused(Problem.TOO_MANY_TOOLS))
    }

    @Test
    fun `parameters that are not a JSON object are refused`() {
        assertThat(decide(names = listOf("find_books"), schemas = listOf("""{"type": "object"""")))
            .isEqualTo(ToolUseDecision.Refused(Problem.SCHEMA_NOT_AN_OBJECT))
        assertThat(decide(names = listOf("find_books"), schemas = listOf("[]")))
            .isEqualTo(ToolUseDecision.Refused(Problem.SCHEMA_NOT_AN_OBJECT))
    }

    @Test
    fun `results that follow their calls one by one, in the calls' order, are accepted`() {
        assertThat(history(calls("a", "b"), answer("a"), answer("b"), Turn(Turn.Kind.ASSISTANT), question))
            .isEqualTo(ToolUseDecision.Accepted)
        assertThat(history(calls("a"), answer("a"), calls("b"), answer("b"))).isEqualTo(ToolUseDecision.Accepted)
    }

    @Test
    fun `results out of order, missing, extra or answering no call are refused rather than reordered`() {
        val refused = ToolUseDecision.Refused(Problem.RESULTS_NOT_IN_CALL_ORDER)

        assertThat(history(calls("a", "b"), answer("b"), answer("a"))).isEqualTo(refused)
        assertThat(history(calls("a", "b"), answer("a"))).isEqualTo(refused)
        assertThat(history(calls("a", "b"), answer("a"), question, answer("b"))).isEqualTo(refused)
        assertThat(history(calls("a"), answer("a"), answer("a"))).isEqualTo(refused)
        assertThat(history(answer("a"))).isEqualTo(refused)
    }

    @Test
    fun `with thinking on and tools offered, every earlier assistant turn sends its thinking back`() {
        assertThat(history(calls("a", reasoned = true), answer("a"), thinking = true))
            .isEqualTo(ToolUseDecision.Accepted)
        assertThat(history(calls("a"), answer("a"), thinking = true))
            .isEqualTo(ToolUseDecision.Refused(Problem.REASONING_NOT_SENT_BACK))
        // Even a turn that called no tool, as the guide says.
        assertThat(history(Turn(Turn.Kind.ASSISTANT), question, thinking = true))
            .isEqualTo(ToolUseDecision.Refused(Problem.REASONING_NOT_SENT_BACK))
    }

    @Test
    fun `without thinking, or without tools, no thinking is owed`() {
        assertThat(history(calls("a"), answer("a"))).isEqualTo(ToolUseDecision.Accepted)
        assertThat(
            ToolUsePolicy.decide(
                offer = ToolUsePolicy.Offer(names = emptyList(), schemas = emptyList(), forced = null, thinking = true),
                turns = listOf(question, Turn(Turn.Kind.ASSISTANT), question),
            ),
        ).isEqualTo(ToolUseDecision.Accepted)
    }
}
