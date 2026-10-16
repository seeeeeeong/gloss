package gloss.domain.chat.presentation

import gloss.domain.chat.service.TurnEvent
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TurnEventStreamTest {
    private val json = JsonMapper()

    @Test
    fun `events are written in the order handed over, each a server-sent event, before the stream closes`() {
        val response = MockHttpServletResponse()

        TurnEventStream(response, json).use { events ->
            val stream = events.stream()
            stream.send(TurnEvent.Text("첫\n조각"))
            stream.send(TurnEvent.Text("둘째"))
        }

        assertThat(response.contentType).startsWith("text/event-stream")
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache, no-transform")
        assertThat(response.contentAsString).isEqualTo(
            "event: text\ndata: {\"text\":\"첫\\n조각\"}\n\nevent: text\ndata: {\"text\":\"둘째\"}\n\n",
        )
    }

    @Test
    fun `nothing is committed before the first event, so an error can still be answered as one`() {
        val response = MockHttpServletResponse()

        TurnEventStream(response, json).use { }

        assertThat(response.contentType).isNull()
        assertThat(response.isCommitted).isFalse()
    }

    @Test
    fun `a write that fails means the reader has gone, and what comes after is dropped`() {
        val failed = CountDownLatch(1)
        val response = Gone(failed)

        TurnEventStream(response, json).use { events ->
            val stream = events.stream()
            stream.send(TurnEvent.Text("조각"))
            assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue()
            awaitClosed(stream.isClosed)
            stream.send(TurnEvent.Text("다음"))

            assertThat(stream.isClosed()).isTrue()
        }
    }

    // The writer marks the stream right after its write fails; a test may look a moment before.
    private fun awaitClosed(closed: () -> Boolean) {
        val latch = CountDownLatch(1)
        val watcher =
            Thread.ofVirtual().start {
                while (!closed()) Thread.onSpinWait()
                latch.countDown()
            }
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        watcher.join()
    }

    /** A response whose reader has gone: every write fails, as a closed connection's does. */
    private class Gone(
        private val failed: CountDownLatch,
    ) : MockHttpServletResponse() {
        override fun getOutputStream(): ServletOutputStream =
            object : ServletOutputStream() {
                override fun write(b: Int) {
                    failed.countDown()
                    throw IOException("Broken pipe")
                }

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = write(0)

                override fun isReady() = true

                override fun setWriteListener(writeListener: WriteListener?) = Unit
            }
    }
}
