package gloss.domain.chat.presentation

import gloss.domain.chat.service.TurnEvent
import gloss.domain.chat.service.TurnStream
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.http.HttpServletResponse
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A turn's events as server-sent events, written by a thread of their own: the call hands each one
 * over and never waits on a slow reader. A write that fails means the reader has gone, which the
 * call takes as a stop. Closing waits for what was handed over to be written.
 */
internal class TurnEventStream(
    private val response: HttpServletResponse,
    private val objectMapper: ObjectMapper,
) : AutoCloseable {
    private val frames = LinkedBlockingQueue<ByteArray>()
    private val gone = AtomicBoolean(false)
    private val lock = Any()
    private var writer: Thread? = null

    fun stream() = TurnStream(send = ::send, isClosed = gone::get)

    private fun send(event: TurnEvent) {
        if (gone.get()) return
        frames.add(frameOf(event))
        synchronized(lock) {
            if (writer == null) writer = start()
        }
    }

    private fun frameOf(event: TurnEvent): ByteArray {
        val (name, data) =
            when (event) {
                is TurnEvent.Asked -> "asked" to event.message
                is TurnEvent.Text -> "text" to mapOf("text" to event.text)
                is TurnEvent.Answered -> "answered" to event.message
            }
        return "event: $name\ndata: ${objectMapper.writeValueAsString(data)}\n\n".toByteArray(Charsets.UTF_8)
    }

    private fun start(): Thread {
        response.contentType = "text/event-stream"
        response.characterEncoding = "UTF-8"
        // A proxy that buffers or compresses would hold the answer back until it ends.
        response.setHeader("Cache-Control", "no-cache, no-transform")
        response.setHeader("X-Accel-Buffering", "no")
        val out = response.outputStream
        return Thread.ofVirtual().name("turn-events").start { drain(out) }
    }

    @Suppress("SwallowedException")
    private fun drain(out: ServletOutputStream) {
        try {
            while (true) {
                // A comment now and then: a reader who left is found even while the model is quiet.
                val frame = frames.poll(KEEP_ALIVE.toMillis(), TimeUnit.MILLISECONDS) ?: PING
                if (frame === END) return
                out.write(frame)
                out.flush()
            }
        } catch (e: IOException) {
            gone.set(true)
        } catch (e: InterruptedException) {
            gone.set(true)
        }
    }

    override fun close() {
        val thread = synchronized(lock) { writer } ?: return
        frames.add(END)
        thread.join(DRAIN)
        if (thread.isAlive) {
            gone.set(true)
            thread.interrupt()
        }
    }

    private companion object {
        val END = ByteArray(0)
        val PING = ": \n\n".toByteArray(Charsets.UTF_8)

        // A reader who left is found within five seconds while the model is quiet, so the call stops
        // billing soon after; far under any proxy's idle timeout (Cloudflare's is 100 seconds).
        val KEEP_ALIVE: Duration = Duration.ofSeconds(5)

        // The end of an answer is a few kilobytes: a reader still there takes it in well under this, and
        // one that does not within it is let go so the request thread is not held for them.
        val DRAIN: Duration = Duration.ofSeconds(30)
    }
}
