package gloss.common.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import java.time.Duration

class TracesTest {
    @Test
    fun `a trace names each cause, without its message`() {
        val trace = traceOf(IllegalStateException("sk-secret", IllegalArgumentException("reader text")))

        assertThat(trace).contains("java.lang.IllegalStateException", "java.lang.IllegalArgumentException")
        assertThat(trace).doesNotContain("sk-secret", "reader text")
    }

    @Test
    fun `causes that point at each other are traced once each`() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second", first)
        first.initCause(second)

        val trace = assertTimeoutPreemptively(Duration.ofSeconds(5), ThrowingSupplier { traceOf(first) })

        assertThat(trace.lines().filter { !it.startsWith("\tat") && it.isNotBlank() })
            .containsExactly("java.lang.IllegalStateException", "java.lang.IllegalArgumentException")
    }
}
