package gloss.domain.llm.service

import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.apikey.dto.request.ApiKeySaveRequest
import gloss.domain.apikey.service.ApiKeyService
import gloss.domain.apikey.service.KeyAccess
import gloss.domain.budget.dto.request.LimitRequest
import gloss.domain.budget.service.BudgetError
import gloss.domain.budget.service.BudgetService
import gloss.domain.budget.service.Outcome
import gloss.domain.budget.service.Purpose
import gloss.domain.budget.service.Reservation
import gloss.domain.llm.FakeLlmGateway
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.ReplyFormat
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.llm.domain.chat.ToolChoice
import gloss.domain.llm.domain.chat.ToolDefinition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeKeyCheckGateway.Config::class, FakeLlmGateway.Config::class)
@MockitoSpyBean(types = [ApiKeyService::class, BudgetService::class])
class LlmServiceTest(
    @Autowired private val llmService: LlmService,
    @Autowired private val fakeLlmGateway: FakeLlmGateway,
    @Autowired private val apiKeyService: ApiKeyService,
    @Autowired private val budgetService: BudgetService,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val transactionTemplate: TransactionTemplate,
) {
    // Committed, as the service commits its own steps; cleared after each test. The ledger is
    // append-only and holds no key to an account, so its rows stay.
    private val created = mutableListOf<UUID>()

    @AfterEach
    fun clear() {
        created.forEach { id ->
            jdbc.sql("delete from api_key where account_id = :id").param("id", id).update()
            jdbc.sql("delete from spending_limit where account_id = :id").param("id", id).update()
            jdbc.sql("delete from account where id = :id").param("id", id).update()
        }
        created.clear()
    }

    // Not a real key: the fake check accepts anything well formed.
    private val typed = "sk-0123456789abcdef"
    private val messages = listOf(ChatMessage(Role.SYSTEM, "rules"), ChatMessage(Role.USER, "question"))

    // 20 hits, 10 misses, 7 out at deepseek-flash's peak prices: 0.12 + 3 + 8.4 = 11.52, so 12 µUSD.
    private val usage = TokenUsage(cacheHitTokens = 20, cacheMissTokens = 10, completionTokens = 7, reasoningTokens = 0)

    @BeforeEach
    fun reset() {
        fakeLlmGateway.reset()
    }

    private fun account(
        key: Boolean = true,
        limitMicros: Long? = 1_000_000,
    ): UUID {
        val id = UUID.randomUUID()
        created += id
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", id)
            .param("loginId", "l-" + id.toString().take(12))
            .update()
        if (key) apiKeyService.save(id, ApiKeySaveRequest(typed))
        if (limitMicros != null) budgetService.setLimit(id, LimitRequest(limitMicros))
        return id
    }

    private fun call(
        account: UUID,
        maxTokens: Int = 100,
    ) = LlmCall(
        accountId = account,
        purpose = Purpose.CHAT,
        messages = messages,
        maxTokens = maxTokens,
        thinking = Thinking.DISABLED,
    )

    private fun failsWith(
        kind: LlmCallFailed.Kind,
        outputStarted: Boolean = false,
        promptMayHaveBilled: Boolean = false,
    ) {
        fakeLlmGateway.answer = { _, _ ->
            throw LlmCallFailed(kind, outputStarted = outputStarted, promptMayHaveBilled = promptMayHaveBilled)
        }
    }

    private fun assertFails(
        error: ErrorCode,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    private fun reservations(account: UUID): List<UUID?> =
        jdbc
            .sql("select reservation_id from usage_ledger where account_id = :id and kind = 'reserve'")
            .param("id", account)
            .query(UUID::class.java)
            .list()

    private fun endings(account: UUID): List<String?> =
        jdbc
            .sql(
                """
                select kind || ':' || coalesce(outcome, '') || ':' || usage_estimated from usage_ledger
                where account_id = :id and kind <> 'reserve'
                """.trimIndent(),
            ).param("id", account)
            .query(String::class.java)
            .list()

    @Test
    fun `a call streams on the reader's key and is settled with what it used`() {
        val account = account()
        fakeLlmGateway.answer = { onText, _ ->
            onText("Hel")
            onText("lo")
            ChatResult("Hello", FinishReason.STOP, usage)
        }
        val pieces = mutableListOf<String>()

        val reply = llmService.stream(call(account), onText = { pieces += it })

        assertThat(reply.text).isEqualTo("Hello")
        assertThat(reply.finishReason).isEqualTo(FinishReason.STOP)
        assertThat(reply.model).isEqualTo("deepseek-flash")
        assertThat(reply.usage).isEqualTo(
            ReplyUsage(cacheHitTokens = 20, cacheMissTokens = 10, completionTokens = 7, reasoningTokens = 0),
        )
        assertThat(reservations(account)).containsExactly(reply.reservationId)
        assertThat(pieces).containsExactly("Hel", "lo")
        assertThat(fakeLlmGateway.secrets).containsExactly(typed)
        assertThat(fakeLlmGateway.requests.single().model).isEqualTo("deepseek-flash")
        assertThat(endings(account)).containsExactly("settle:stop:false")
        assertThat(budgetService.usage(account).spentMicros).isEqualTo(12)
        assertThat(
            jdbc
                .sql(
                    "select last_used_at from api_key where account_id = :id",
                ).param("id", account)
                .query()
                .singleValue(),
        ).isNotNull()
    }

    @Test
    fun `a call for JSON reaches the provider as one`() {
        val account = account()
        fakeLlmGateway.answer = { _, _ -> ChatResult("{}", FinishReason.STOP, usage) }

        llmService.stream(call(account).copy(format = ReplyFormat.JSON), onText = {})

        assertThat(fakeLlmGateway.requests.single().format).isEqualTo(ReplyFormat.JSON)
    }

    private val searchWeb =
        ToolDefinition(
            name = "search_web",
            description = "Search the web",
            parameters = """{"type":"object","properties":{"query":{"type":"string"}}}""",
        )

    @Test
    fun `a call that ends in tool calls is settled with what it used as such, and hands the calls back`() {
        val account = account()
        val called = ToolCall(id = "call_a", name = "search_web", arguments = """{"query": "os"}""")
        fakeLlmGateway.answer = { _, _ ->
            ChatResult(text = "", finishReason = FinishReason.TOOL_CALLS, usage = usage, toolCalls = listOf(called))
        }

        val reply =
            llmService.stream(
                call(account).copy(tools = listOf(searchWeb), toolChoice = ToolChoice.Required),
                onText = {},
            )

        assertThat(reply.finishReason).isEqualTo(FinishReason.TOOL_CALLS)
        assertThat(reply.toolCalls).containsExactly(called)
        assertThat(reply.usage).isEqualTo(
            ReplyUsage(cacheHitTokens = 20, cacheMissTokens = 10, completionTokens = 7, reasoningTokens = 0),
        )
        assertThat(fakeLlmGateway.requests.single().tools).containsExactly(searchWeb)
        assertThat(fakeLlmGateway.requests.single().toolChoice).isEqualTo(ToolChoice.Required)
        assertThat(reservations(account)).containsExactly(reply.reservationId)
        assertThat(endings(account)).containsExactly("settle:tool_calls:false")
        assertThat(budgetService.usage(account).spentMicros).isEqualTo(12)
    }

    @Test
    fun `a call offering tools that fails before billing is released`() {
        val account = account()
        failsWith(LlmCallFailed.Kind.RATE_LIMITED)

        assertFails(LlmError.PROVIDER_BUSY) {
            llmService.stream(call(account).copy(tools = listOf(searchWeb)), onText = {})
        }
        assertThat(endings(account)).containsExactly("release:error:false")
        assertThat(budgetService.usage(account).inFlightMicros).isZero()
    }

    @Test
    fun `a call offering tools cut mid-call is settled at the most it could have cost`() {
        val account = account()
        failsWith(LlmCallFailed.Kind.NETWORK, outputStarted = true)

        assertFails(LlmError.PROVIDER_FAILED) {
            llmService.stream(call(account).copy(tools = listOf(searchWeb)), onText = {})
        }
        assertThat(endings(account)).containsExactly("settle:error:true")
        assertThat(budgetService.usage(account).inFlightMicros).isZero()
    }

    @Test
    fun `forcing a tool with thinking on is refused before anything is reserved or sent`() {
        val account = account()
        val forcedWhileThinking =
            call(account).copy(
                thinking = Thinking.ENABLED,
                tools = listOf(searchWeb),
                toolChoice = ToolChoice.Named("search_web"),
            )

        assertFails(LlmError.TOOLS_REFUSED) { llmService.stream(forcedWhileThinking, onText = {}) }
        assertThat(fakeLlmGateway.requests).isEmpty()
        assertThat(reservations(account)).isEmpty()
    }

    @Test
    fun `tool results out of their calls' order, or thinking not sent back, are refused before anything is reserved`() {
        val account = account()
        val calls =
            ChatMessage(
                role = Role.ASSISTANT,
                content = "",
                toolCalls = listOf(ToolCall("call_a", "search_web", "{}"), ToolCall("call_b", "search_web", "{}")),
            )
        val swapped =
            messages + calls + ChatMessage.toolResult("call_b", "b") + ChatMessage.toolResult("call_a", "a")
        val inOrder =
            messages + calls + ChatMessage.toolResult("call_a", "a") + ChatMessage.toolResult("call_b", "b")

        assertFails(LlmError.TOOLS_REFUSED) {
            llmService.stream(call(account).copy(messages = swapped, tools = listOf(searchWeb)), onText = {})
        }
        assertFails(LlmError.TOOLS_REFUSED) {
            llmService.stream(
                call(account).copy(messages = inOrder, tools = listOf(searchWeb), thinking = Thinking.ENABLED),
                onText = {},
            )
        }
        assertThat(fakeLlmGateway.requests).isEmpty()
        assertThat(reservations(account)).isEmpty()
    }

    @Test
    fun `a call without tools reaches the gateway offering none`() {
        val account = account()
        fakeLlmGateway.answer = { _, _ -> ChatResult("ok", FinishReason.STOP, usage) }

        llmService.stream(call(account), onText = {})

        assertThat(fakeLlmGateway.requests.single().tools).isEmpty()
        assertThat(fakeLlmGateway.requests.single().toolChoice).isEqualTo(ToolChoice.Auto)
    }

    @Test
    fun `without a key nothing is reserved or sent`() {
        val account = account(key = false)

        assertFails(LlmError.NO_KEY) { llmService.stream(call(account), onText = {}) }
        assertThat(fakeLlmGateway.requests).isEmpty()
        assertThat(budgetService.usage(account).inFlightMicros).isZero()
    }

    @Test
    fun `without a monthly limit nothing is sent`() {
        val account = account(limitMicros = null)

        assertFails(BudgetError.LIMIT_NOT_SET) { llmService.stream(call(account), onText = {}) }
        assertThat(fakeLlmGateway.requests).isEmpty()
    }

    @Test
    fun `a key the provider refuses is released, marked and not used again`() {
        val account = account()
        failsWith(LlmCallFailed.Kind.REJECTED_KEY)

        assertFails(LlmError.KEY_REJECTED) { llmService.stream(call(account), onText = {}) }
        assertThat(endings(account)).containsExactly("release:error:false")
        assertThat(apiKeyService.access(account)).isEqualTo(KeyAccess.Rejected)
    }

    @Test
    fun `a rate limit is released and answered as busy`() {
        val account = account()
        failsWith(LlmCallFailed.Kind.RATE_LIMITED)

        assertFails(LlmError.PROVIDER_BUSY) { llmService.stream(call(account), onText = {}) }
        assertThat(endings(account)).containsExactly("release:error:false")
    }

    @Test
    fun `a request the provider refuses is released, and answered as one that would be refused again`() {
        val account = account()
        failsWith(LlmCallFailed.Kind.REFUSED_REQUEST)

        assertFails(LlmError.REQUEST_REFUSED) { llmService.stream(call(account), onText = {}) }
        assertThat(endings(account)).containsExactly("release:error:false")
    }

    @Test
    fun `a call that fails after the provider said what it used carries that and its reservation`() {
        val account = account()
        fakeLlmGateway.answer = { _, _ ->
            throw LlmCallFailed(LlmCallFailed.Kind.PROVIDER_ERROR, outputStarted = true, usage = usage)
        }

        val failed = runCatching { llmService.stream(call(account), onText = {}) }.exceptionOrNull()

        assertThat(failed).isInstanceOfSatisfying(LlmCallError::class.java) {
            assertThat(it.error).isEqualTo(LlmError.PROVIDER_FAILED)
            assertThat(it.usage).isEqualTo(
                ReplyUsage(cacheHitTokens = 20, cacheMissTokens = 10, completionTokens = 7, reasoningTokens = 0),
            )
            assertThat(reservations(account)).containsExactly(it.reservationId)
        }
        assertThat(endings(account)).containsExactly("settle:error:false")
    }

    @Test
    fun `a connection dropped after some text is settled at the most it could have cost, marked estimated`() {
        val account = account()
        failsWith(LlmCallFailed.Kind.NETWORK, outputStarted = true)

        assertFails(LlmError.PROVIDER_FAILED) { llmService.stream(call(account), onText = {}) }
        assertThat(endings(account)).containsExactly("settle:error:true")
        assertThat(budgetService.usage(account).inFlightMicros).isZero()
    }

    @Test
    fun `a call cancelled before any text is settled at its prompt, marked estimated, and reported cancelled`() {
        val account = account()
        // Cancelled once sent: the gateway is what notices, and the provider has the request.
        failsWith(LlmCallFailed.Kind.CANCELLED, promptMayHaveBilled = true)

        val reply = llmService.stream(call(account), onText = {})

        assertThat(reply.cancelled).isTrue()
        assertThat(reservations(account)).containsExactly(reply.reservationId)
        assertThat(endings(account)).containsExactly("settle:cancelled_before_output:true")
    }

    @Test
    fun `anything else thrown mid-call still releases the reservation`() {
        val account = account()
        fakeLlmGateway.answer = { _, _ -> throw IllegalStateException("a bug") }

        assertThatThrownBy {
            llmService.stream(
                call(account),
                onText = {},
            )
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(endings(account)).containsExactly("release:error:false")
    }

    @Test
    fun `a cap above the model's is brought down to it`() {
        val account = account(limitMicros = 1_000_000_000)
        fakeLlmGateway.answer = { _, _ -> ChatResult("ok", FinishReason.STOP, usage) }

        llmService.stream(call(account, maxTokens = Int.MAX_VALUE), onText = {})

        assertThat(fakeLlmGateway.requests.single().maxTokens).isEqualTo(393_216)
    }

    @Test
    fun `a handler that throws mid-stream still pays for what may have billed, and its error comes back`() {
        val account = account()
        val broken = IllegalStateException("the browser went away")
        fakeLlmGateway.answer = { _, _ ->
            throw LlmCallFailed(LlmCallFailed.Kind.CALLER_FAILED, outputStarted = true).apply { initCause(broken) }
        }

        assertThatThrownBy { llmService.stream(call(account), onText = {}) }.isSameAs(broken)
        assertThat(endings(account)).containsExactly("settle:error:true")
    }

    @Test
    fun `a call already cancelled reserves and sends nothing`() {
        val account = account()

        val reply = llmService.stream(call(account), onText = {}, isCancelled = { true })

        assertThat(reply.cancelled).isTrue()
        assertThat(reply.reservationId).isNull()
        assertThat(fakeLlmGateway.requests).isEmpty()
        assertThat(endings(account)).isEmpty()
        assertThat(budgetService.usage(account).inFlightMicros).isZero()
    }

    @Test
    fun `a call cancelled once reserved is released before anything is sent`() {
        val account = account()
        var asked = 0

        val reply = llmService.stream(call(account), onText = {}, isCancelled = { asked++ > 0 })

        assertThat(reply.cancelled).isTrue()
        assertThat(fakeLlmGateway.requests).isEmpty()
        assertThat(endings(account)).containsExactly("release:cancelled_before_output:false")
    }

    @Test
    fun `a prompt that leaves no room in the context is refused before anything is reserved`() {
        val account = account()
        val huge =
            LlmCall(
                accountId = account,
                purpose = Purpose.CHAT,
                messages = listOf(ChatMessage(Role.USER, "a".repeat(1_000_000))),
                maxTokens = 100,
                thinking = Thinking.DISABLED,
            )

        assertFails(LlmError.PROMPT_TOO_LONG) { llmService.stream(huge, onText = {}) }
        assertThat(fakeLlmGateway.requests).isEmpty()
        assertThat(budgetService.usage(account).inFlightMicros).isZero()
    }

    @Test
    fun `a cap is lowered to what the prompt leaves of the context`() {
        val account = account()
        fakeLlmGateway.answer = { _, _ -> ChatResult("ok", FinishReason.STOP, usage) }
        val long =
            LlmCall(
                accountId = account,
                purpose = Purpose.CHAT,
                messages = listOf(ChatMessage(Role.USER, "a".repeat(700_000))),
                maxTokens = 393_216,
                thinking = Thinking.DISABLED,
            )

        llmService.stream(long, onText = {})

        // 700,000 bytes, 8 for the message's framing and 32 for the call's, out of a 1,000,000-token context.
        assertThat(fakeLlmGateway.requests.single().maxTokens).isEqualTo(1_000_000 - 700_040)
    }

    @Test
    fun `a connection lost after the request went out, before any answer, is settled at its prompt`() {
        val account = account()
        failsWith(LlmCallFailed.Kind.NETWORK, promptMayHaveBilled = true)

        assertFails(LlmError.PROVIDER_FAILED) { llmService.stream(call(account), onText = {}) }
        assertThat(endings(account)).containsExactly("settle:error:true")
    }

    @Test
    fun `a cancel check that throws once reserved still releases the reservation`() {
        val account = account()
        var asked = 0
        val failingSecondTime = {
            check(asked++ == 0) { "session lookup failed" }
            false
        }

        assertThatThrownBy { llmService.stream(call(account), onText = {}, isCancelled = failingSecondTime) }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(endings(account)).containsExactly("release:error:false")
        assertThat(fakeLlmGateway.requests).isEmpty()
    }

    @Test
    fun `a call inside a caller's transaction keeps its ledger rows when that transaction rolls back`() {
        val account = account()
        fakeLlmGateway.answer = { _, _ -> ChatResult("ok", FinishReason.STOP, usage) }

        transactionTemplate.executeWithoutResult { status ->
            llmService.stream(call(account), onText = {})
            status.setRollbackOnly()
        }

        assertThat(endings(account)).containsExactly("settle:stop:false")
    }

    @Test
    fun `a settle that fails once the call went out leaves the reservation to expire at its full amount`() {
        val account = account()
        // A count the ledger refuses, so settling throws after the provider has answered.
        val refused = usage.copy(completionTokens = -1)
        fakeLlmGateway.answer = { _, _ -> ChatResult("ok", FinishReason.STOP, refused) }

        assertThatThrownBy { llmService.stream(call(account), onText = {}) }.isNotNull()
        assertThat(endings(account)).isEmpty()
        assertThat(budgetService.usage(account).inFlightMicros).isPositive()
    }

    @Test
    fun `a use that fails to be recorded does not take back a complete answer`() {
        val account = account()
        fakeLlmGateway.answer = { _, _ -> ChatResult("Hello", FinishReason.STOP, usage) }
        doThrow(IllegalStateException("database blinked")).`when`(apiKeyService).recordUse(anyId())

        val reply = llmService.stream(call(account), onText = {})

        assertThat(reply.text).isEqualTo("Hello")
        assertThat(endings(account)).containsExactly("settle:stop:false")
    }

    // Mockito's matcher returns null, which a Kotlin UUID parameter refuses; the matcher is what counts.
    private fun anyId(): UUID = ArgumentMatchers.any(UUID::class.java) ?: UUID(0, 0)

    @Test
    fun `a release that fails for a call that billed nothing is tried again, so the reservation never stays open`() {
        val account = account()
        failsWith(LlmCallFailed.Kind.RATE_LIMITED)
        doThrow(
            IllegalStateException("database blinked"),
        ).doCallRealMethod().`when`(budgetService).release(anyReservation(), anyOutcome())

        assertThatThrownBy {
            llmService.stream(
                call(account),
                onText = {},
            )
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(endings(account)).containsExactly("release:error:false")
    }

    private fun anyReservation(): Reservation =
        ArgumentMatchers.any(Reservation::class.java) ?: Reservation(UUID(0, 0), UUID(0, 0), 0)

    private fun anyOutcome(): Outcome = ArgumentMatchers.any(Outcome::class.java) ?: Outcome.ERROR
}
