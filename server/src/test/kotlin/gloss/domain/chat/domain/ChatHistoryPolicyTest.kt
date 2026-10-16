package gloss.domain.chat.domain

import gloss.domain.chat.domain.ChatHistoryPolicy.Exchange
import gloss.domain.chat.domain.ChatHistoryPolicy.Last
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ChatHistoryPolicyTest {
    private val plan = UUID.randomUUID()
    private val post = UUID.randomUUID()

    /** [turns] finished turns from seq 1, every other number a reply, each prompt and reply [size] bytes. */
    private fun finished(
        turns: Int,
        size: Int = 1_000,
    ): List<Exchange> =
        (1..turns).map { n ->
            Exchange(seq = 2 * n - 1, prompt = "p$n".padEnd(size, '.'), reply = "a$n".padEnd(size, '.'))
        }

    /** The window each turn of a conversation kept, as the service builds it turn after turn. */
    private fun conversation(
        turns: Int,
        budget: Long,
    ): List<ChatHistoryPolicy.Window> {
        val all = finished(turns)
        var last: Last? = null
        return (1..turns).map { n ->
            val window =
                ChatHistoryPolicy.window(
                    last = last,
                    planId = plan,
                    finished = all.take(n - 1),
                    next =
                        2 * n - 1,
                    budget = budget,
                )
            last = Last(window.from, plan)
            window
        }
    }

    @Test
    fun `the first turn, and the first under a new plan, start the history afresh`() {
        val first = ChatHistoryPolicy.window(last = null, planId = plan, finished = emptyList(), next = 1)
        val replanned =
            ChatHistoryPolicy.window(
                last = Last(1, UUID.randomUUID()),
                planId = plan,
                finished = finished(3),
                next = 7,
            )

        assertThat(first).isEqualTo(ChatHistoryPolicy.Window(from = 1, exchanges = emptyList()))
        assertThat(replanned).isEqualTo(ChatHistoryPolicy.Window(from = 7, exchanges = emptyList()))
    }

    @Test
    fun `finished turns from the last start go back as sent, while they fit`() {
        val window = ChatHistoryPolicy.window(last = Last(3, plan), planId = plan, finished = finished(3), next = 7)

        assertThat(window.from).isEqualTo(3)
        assertThat(window.exchanges.map { it.seq }).containsExactly(3, 5)
    }

    @Test
    fun `a long conversation sends its newest turns within the budget`() {
        val windows = conversation(turns = 300, budget = ChatHistoryPolicy.HISTORY_TOKENS)

        assertThat(windows).allSatisfy {
            assertThat(
                it.exchanges.sumOf { exchange ->
                    exchange.tokens
                },
            ).isLessThanOrEqualTo(ChatHistoryPolicy.HISTORY_TOKENS)
        }
        assertThat(
            windows
                .last()
                .exchanges
                .last()
                .seq,
        ).isEqualTo(2 * 299 - 1)
        assertThat(windows.last().exchanges).hasSizeGreaterThan(10)
    }

    @Test
    fun `the start moves only when the turns outgrow it, and what was sent stays a prefix in between`() {
        val windows = conversation(turns = 60, budget = 40_000)

        val moves =
            windows.zipWithNext().count { (before, after) ->
                after.exchanges.take(before.exchanges.size) !=
                    before.exchanges
            }
        assertThat(moves).isBetween(1, 7)
    }

    @Test
    fun `a post goes in until a turn sent again carries it, and again once that turn has left`() {
        val carried = Exchange(seq = 1, prompt = "p", reply = "a", postVersionId = post)
        val kept = ChatHistoryPolicy.window(last = Last(1, plan), planId = plan, finished = listOf(carried), next = 3)
        val gone = ChatHistoryPolicy.window(last = Last(3, plan), planId = plan, finished = listOf(carried), next = 5)

        assertThat(kept.sendsPost(post)).isFalse()
        assertThat(kept.sendsPost(UUID.randomUUID())).isTrue()
        assertThat(gone.sendsPost(post)).isTrue()
    }

    @Test
    fun `turns that never finished do not hold the start back`() {
        val failedRun = ChatHistoryPolicy.window(last = Last(1, plan), planId = plan, finished = emptyList(), next = 41)
        val afterOne =
            ChatHistoryPolicy.window(
                last = Last(1, plan),
                planId = plan,
                finished = listOf(Exchange(35, "p", "a")),
                next = 41,
            )

        assertThat(failedRun.from).isEqualTo(41)
        assertThat(afterOne.from).isEqualTo(35)
    }
}
