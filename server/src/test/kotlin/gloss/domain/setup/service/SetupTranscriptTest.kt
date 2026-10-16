package gloss.domain.setup.service

import gloss.domain.llm.domain.chat.PromptSize
import gloss.domain.llm.domain.chat.Role
import gloss.domain.setup.domain.BooksFound
import gloss.domain.setup.domain.CalledTool
import gloss.domain.setup.domain.ContentsBrought
import gloss.domain.setup.domain.FoundEdition
import gloss.domain.setup.domain.GlossSaid
import gloss.domain.setup.domain.HistoryPolicy
import gloss.domain.setup.domain.ReaderPicked
import gloss.domain.setup.domain.ReaderSaid
import gloss.domain.setup.domain.ReaderTyped
import gloss.domain.setup.domain.SearchFailed
import gloss.domain.setup.domain.SetupContent
import gloss.domain.setup.domain.ToolRefused
import gloss.domain.setup.domain.ToolsCalled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class SetupTranscriptTest {
    private val transcript = SetupTranscript(JsonMapper())

    private fun answered(i: Int): List<SetupContent> =
        listOf(
            ReaderSaid("질문 $i " + "가".repeat(400)),
            GlossSaid("답 $i " + "나".repeat(1_000)),
        )

    @Test
    fun `a long session is read as its newest answered turns within the budget, and the message being answered`() {
        val contents = (1..200).flatMap(::answered) + ReaderSaid("마지막 질문")

        val messages = transcript.of(contents)

        assertThat(PromptSize.upperBound(messages)).isLessThanOrEqualTo(HistoryPolicy.BUDGET + 100)
        assertThat(messages.last().content).isEqualTo("마지막 질문")
        assertThat(messages.first().content).startsWith("질문 ")
        assertThat(messages[messages.size - 2].content).startsWith("답 200 ")
    }

    @Test
    fun `a turn that failed or never ended is never read again`() {
        val failed =
            listOf(
                ReaderSaid("실패한 질문"),
                ToolsCalled("", listOf(CalledTool(id = "c1", name = "find_books", arguments = "{}"))),
                ToolRefused(callId = "c1", problem = "the book search failed"),
                SearchFailed(code = "llm.prompt_too_long", message = "너무 깁니다"),
            )
        val unended = listOf(ReaderSaid("끝나지 않은 질문"))
        val contents = answered(1) + failed + unended + ReaderSaid("지금 질문")

        val messages = transcript.of(contents)

        assertThat(messages.map { it.role }).containsExactly(Role.USER, Role.ASSISTANT, Role.USER)
        assertThat(messages.map { it.content }.joinToString()).doesNotContain("실패한", "끝나지", "c1")
        assertThat(messages.last().content).isEqualTo("지금 질문")
    }

    @Test
    fun `a pick and typed contents are read as the reader's words, the same bytes every turn`() {
        val picked =
            answered(1) +
                listOf(
                    ReaderPicked(ref = "101", title = "오브젝트"),
                    ContentsBrought(ref = "101", title = "오브젝트", items = 3, typed = false),
                    ReaderSaid("이 책보다 쉬운 책도 알려줘"),
                )
        val later = picked + GlossSaid("이 책을 권해요.") + ReaderTyped("나의 노트") + ReaderSaid("고마워요")

        val first = transcript.of(picked)
        val second = transcript.of(later)

        assertThat(first.last().content).isEqualTo("「오브젝트」을(를) 골랐어요.\n이 책보다 쉬운 책도 알려줘")
        assertThat(second.take(first.size)).isEqualTo(first)
        assertThat(second.last().content).isEqualTo("목차를 직접 입력했어요: 나의 노트\n고마워요")
    }

    @Test
    fun `a title search from before models took part is read as Gloss's answer, its refs alone`() {
        val legacy =
            listOf(
                ReaderSaid("오브젝트"),
                BooksFound(query = "오브젝트", books = listOf(FoundEdition(ref = "101", hasContents = true))),
                ReaderSaid("두 번째 책은 어때요?"),
            )

        val messages = transcript.of(legacy)

        assertThat(messages.map { it.role }).containsExactly(Role.USER, Role.ASSISTANT, Role.USER)
        assertThat(messages[1].content).contains("\"ref\":\"101\"").doesNotContain("오브젝트")
    }
}
