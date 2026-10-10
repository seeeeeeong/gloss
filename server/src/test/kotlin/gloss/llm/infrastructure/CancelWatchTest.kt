package gloss.llm.infrastructure

import gloss.llm.domain.LlmCallFailed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException

class CancelWatchTest {
    private val broken = IllegalStateException("session lookup failed")

    @Test
    fun `a failed check the call's failure carries is not handed out again`() {
        val watch = CancelWatch()
        watch.fail(broken)

        val failure = watch.failure(lost = IOException("closed"), outputStarted = true, promptMayHaveBilled = true)

        assertThat(failure.kind).isEqualTo(LlmCallFailed.Kind.CALLER_FAILED)
        assertThat(watch.end()).isNull()
    }

    @Test
    fun `a failed check that nothing carried is handed out when the call ends`() {
        val watch = CancelWatch()

        assertThat(watch.fail(broken)).isNull()
        assertThat(watch.end()).isSameAs(broken)
    }

    @Test
    fun `a check that fails once the call ended is handed out at once, and only once`() {
        val watch = CancelWatch()

        assertThat(watch.end()).isNull()
        assertThat(watch.fail(broken)).isSameAs(broken)
        assertThat(watch.end()).isNull()
    }
}
