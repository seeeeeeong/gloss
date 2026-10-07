package gloss.llm.infrastructure

import gloss.llm.domain.ChatRequest
import gloss.llm.domain.ChatResult
import gloss.llm.domain.LlmCallFailed
import gloss.llm.domain.LlmGateway
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * DeepSeek's OpenAI-compatible chat completions, streamed (api-docs.deepseek.com). Plain
 * HttpURLConnection: its read timeout is an idle timeout between pieces, and disconnect closes
 * the socket, so a cancelled or overdue call stops generating instead of being read to the end.
 * Once a stream is open, disconnect waits for the read under way, so a quiet stream closes at
 * most one idle timeout late, still well inside a reservation's expiry. Pieces go to onText as
 * they come and are provisional: only a result that finished with STOP is an answer. Never logs
 * the key or the text.
 */
@Component
class DeepSeekLlmGateway(
    @Value("\${gloss.deepseek.base-url}") private val baseUrl: String,
    @Value("\${gloss.deepseek.idle-timeout}") private val idleTimeout: Duration,
    @Value("\${gloss.deepseek.call-deadline}") private val deadline: Duration,
) : LlmGateway {
    private val json = JsonMapper()
    private val reader = DeepSeekStreamReader(json)

    // Only marks a call overdue and hands its close to a thread of its own: once a stream is open,
    // closing it waits for the read under way, and no call's close may hold back another's deadline.
    private val deadlines =
        ScheduledThreadPoolExecutor(1) { Thread(it, "llm-deadline").apply { isDaemon = true } }.apply {
            // A call that ends well cancels its deadline; otherwise it would wait out its delay in the queue.
            removeOnCancelPolicy = true
        }

    override fun stream(
        secret: String,
        request: ChatRequest,
        onText: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): ChatResult {
        val body = json.writeValueAsBytes(body(request))
        val connection = connectionFor(secret, body.size)
        val watch = CancelWatch()
        val overdue =
            deadlines.schedule(
                {
                    watch.expire()
                    Thread.ofVirtual().name("llm-deadline-close").start(connection::disconnect)
                },
                deadline.toMillis(),
                TimeUnit.MILLISECONDS,
            )
        val ended = CountDownLatch(1)
        // Before the first piece nothing asks the caller, so this does, while a large prompt goes
        // out or the provider takes its time; a cancel then closes the connection at once. A thread
        // per call, since a caller's check is the caller's code and may never return.
        Thread.ofVirtual().name("llm-cancel-check").start {
            while (!ended.await(CANCEL_POLL.toMillis(), TimeUnit.MILLISECONDS)) {
                closeIfCancelled(connection, isCancelled, watch)
            }
        }
        try {
            connect(connection, watch)
            send(connection, body, watch)
            val status = statusOf(connection, watch)
            // Refused with a status: the provider did not take the request on.
            if (status != HttpURLConnection.HTTP_OK) throw LlmCallFailed(kindOf(status), outputStarted = false)
            return reader.read(connection = connection, onText = onText, isCancelled = isCancelled, watch = watch)
        } finally {
            ended.countDown()
            overdue.cancel(false)
            connection.disconnect()
        }
    }

    // Runs on the call's cancel-check thread. A check that throws is the caller's failure: kept, and the
    // connection closed, so the call ends now and says why.
    @Suppress("TooGenericExceptionCaught")
    private fun closeIfCancelled(
        connection: HttpURLConnection,
        isCancelled: () -> Boolean,
        watch: CancelWatch,
    ) {
        if (watch.closed) return
        try {
            if (isCancelled()) {
                watch.cancel()
                connection.disconnect()
            }
        } catch (e: Exception) {
            watch.fail(e)
            connection.disconnect()
        }
    }

    private fun connectionFor(
        secret: String,
        length: Int,
    ): HttpURLConnection =
        (URI("$baseUrl/chat/completions").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            // Streamed as written, not buffered whole until the response is asked for, so a failed
            // or overdue send shows here and not as an answer that never came.
            setFixedLengthStreamingMode(length)
            connectTimeout = CONNECT_TIMEOUT.toMillis().toInt()
            readTimeout = idleTimeout.toMillis().toInt()
            setRequestProperty("Authorization", "Bearer $secret")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
        }

    // Not connected: nothing reached the provider.
    private fun connect(
        connection: HttpURLConnection,
        watch: CancelWatch,
    ) {
        try {
            connection.connect()
        } catch (e: IOException) {
            throw watch.failure(lost = e, outputStarted = false, promptMayHaveBilled = false)
        }
    }

    // Some or all of the request may have gone out, so its prompt may have billed.
    private fun send(
        connection: HttpURLConnection,
        body: ByteArray,
        watch: CancelWatch,
    ) {
        try {
            connection.outputStream.use { it.write(body) }
        } catch (e: IOException) {
            throw watch.failure(lost = e, outputStarted = false, promptMayHaveBilled = true)
        }
    }

    // Sent, then no answer: the provider may have taken it on and billed the prompt. A connection
    // closed mid-wait can surface from the JDK as a wrapped NullPointerException.
    @Suppress("TooGenericExceptionCaught")
    private fun statusOf(
        connection: HttpURLConnection,
        watch: CancelWatch,
    ): Int =
        try {
            connection.responseCode
        } catch (e: IOException) {
            throw watch.failure(lost = e, outputStarted = false, promptMayHaveBilled = true)
        } catch (e: RuntimeException) {
            throw watch.failure(lost = e, outputStarted = false, promptMayHaveBilled = true)
        }

    private fun body(request: ChatRequest): Map<String, Any> =
        mapOf(
            "model" to request.model,
            "messages" to request.messages.map { mapOf("role" to it.role.code, "content" to it.content) },
            "max_tokens" to request.maxTokens,
            "thinking" to mapOf("type" to request.thinking.code),
            "stream" to true,
            // Without it a stream reports no usage, and the ledger could only guess.
            "stream_options" to mapOf("include_usage" to true),
        )

    private fun kindOf(status: Int): LlmCallFailed.Kind =
        when (status) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> LlmCallFailed.Kind.REJECTED_KEY
            HttpURLConnection.HTTP_PAYMENT_REQUIRED -> LlmCallFailed.Kind.INSUFFICIENT_BALANCE
            TOO_MANY_REQUESTS -> LlmCallFailed.Kind.RATE_LIMITED
            else -> LlmCallFailed.Kind.PROVIDER_ERROR
        }

    private companion object {
        // A TCP and TLS handshake with a healthy API takes well under a second.
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        const val TOO_MANY_REQUESTS = 429

        // How soon a cancel before the first piece closes the call; once pieces flow, each is a check.
        val CANCEL_POLL: Duration = Duration.ofSeconds(1)
    }
}
