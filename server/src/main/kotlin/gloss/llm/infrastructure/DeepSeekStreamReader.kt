package gloss.llm.infrastructure

import gloss.llm.domain.ChatResult
import gloss.llm.domain.LlmCallFailed
import gloss.llm.domain.TokenUsage
import gloss.llm.domain.chat.FinishReason
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection

/** Reads DeepSeek's server-sent events into a result, or into a failure that says what may have billed. */
internal class DeepSeekStreamReader(
    private val json: JsonMapper,
) {
    /** What the stream has given so far, for the result or for saying what a failure may have billed. */
    private class Received {
        val text = StringBuilder()
        var finish: FinishReason? = null
        var usage: TokenUsage? = null

        // Thinking bills as output too, though none of it reaches the text.
        var billed = false

        val finished: Boolean get() = finish != null && usage != null

        // The last chunk carries both; with them in hand a missing [DONE] loses nothing.
        fun complete(): ChatResult? {
            val reason = finish ?: return null
            return usage?.let { ChatResult(text.toString(), reason, it) }
        }

        // A stream is a request the provider took on, so its prompt may have billed.
        fun failed(kind: LlmCallFailed.Kind) =
            LlmCallFailed(kind = kind, outputStarted = billed, usage = usage, promptMayHaveBilled = true)
    }

    /** [watch] says whether the gateway closed the connection for a cancel, or for a check that threw. */
    fun read(
        connection: HttpURLConnection,
        onText: (String) -> Unit,
        isCancelled: () -> Boolean,
        watch: CancelWatch,
    ): ChatResult {
        val received = Received()
        val result =
            try {
                connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use {
                        val lines = linesOf(it, watch)
                        readEvents(lines = lines, received = received, onText = onText, isCancelled = isCancelled)
                    }
            } catch (e: IOException) {
                // Dropped, idle past the timeout, or closed at the deadline or for a cancel.
                return received.complete()
                    ?: throw watch.failure(
                        lost = e,
                        outputStarted = received.billed,
                        promptMayHaveBilled = true,
                        usage = received.usage,
                    )
            }
        // Null: the stream ended without [DONE].
        return result ?: received.complete() ?: throw received.failed(LlmCallFailed.Kind.NETWORK)
    }

    // Closing from outside waits for the read under way, which a lively stream may never yield;
    // the reading thread stops at its next line instead.
    private fun linesOf(
        reader: BufferedReader,
        watch: CancelWatch,
    ): Sequence<String> = reader.lineSequence().onEach { if (watch.closed) throw IOException(CLOSED) }

    private fun readEvents(
        lines: Sequence<String>,
        received: Received,
        onText: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): ChatResult? {
        for (line in lines) {
            // Finished and billed: a cancel now would only throw the answer away, as a close at this
            // point does not either.
            if (!received.finished && cancelled(received, isCancelled)) {
                throw received.failed(LlmCallFailed.Kind.CANCELLED)
            }
            // Blank lines end an event; a line starting with ':' is a comment, as DeepSeek's keep-alive.
            val data = line.takeIf { it.startsWith(DATA) }?.removePrefix(DATA)?.trim() ?: continue
            if (data == DONE) {
                return ChatResult(received.text.toString(), received.finish ?: FinishReason.UNKNOWN, received.usage)
            }
            take(data, received, onText)
        }
        return null
    }

    // The caller's check may throw anything; whatever it is, what the call may have billed is still owed.
    @Suppress("TooGenericExceptionCaught")
    private fun cancelled(
        received: Received,
        isCancelled: () -> Boolean,
    ): Boolean =
        try {
            isCancelled()
        } catch (e: Exception) {
            throw received.failed(LlmCallFailed.Kind.CALLER_FAILED).apply { initCause(e) }
        }

    // The caller's handler may throw anything, a write to its own client's socket included; it is the
    // caller's failure, never the provider's, and what the call may have billed is still owed.
    @Suppress("TooGenericExceptionCaught")
    private fun take(
        data: String,
        received: Received,
        onText: (String) -> Unit,
    ) {
        val event =
            try {
                json.readTree(data)
            } catch (e: JacksonException) {
                throw received.failed(LlmCallFailed.Kind.PROVIDER_ERROR).apply { initCause(e) }
            }
        val choice = event.path("choices").path(0)
        val delta = choice.path("delta")
        if (delta.path("reasoning_content").stringOrNull()?.isNotEmpty() == true) received.billed = true
        delta.path("content").stringOrNull()?.takeIf { it.isNotEmpty() }?.let { piece ->
            received.billed = true
            received.text.append(piece)
            try {
                onText(piece)
            } catch (e: Exception) {
                throw received.failed(LlmCallFailed.Kind.CALLER_FAILED).apply { initCause(e) }
            }
        }
        choice.path("finish_reason").stringOrNull()?.let { received.finish = FinishReason.of(it) }
        event.path("usage").takeIf { it.isObject }?.let { received.usage = usageOf(it) ?: received.usage }
    }

    // Every count a whole non-negative number, or no usage at all: a missing field read as 0
    // would settle a billed call as free, while no usage settles it by estimate.
    private fun usageOf(node: JsonNode): TokenUsage? {
        val hit = node.path("prompt_cache_hit_tokens").countOrNull()
        val miss = node.path("prompt_cache_miss_tokens").countOrNull()
        val completion = node.path("completion_tokens").countOrNull()
        if (hit == null || miss == null || completion == null) return null
        // DeepSeek nests it as OpenAI does; a flat field is read too, should it move.
        val reasoning =
            node.path("completion_tokens_details").path("reasoning_tokens").countOrNull()
                ?: node.path("reasoning_tokens").countOrNull()
        return TokenUsage(
            cacheHitTokens = hit,
            cacheMissTokens = miss,
            completionTokens = completion,
            reasoningTokens = reasoning,
        )
    }

    private fun JsonNode.countOrNull(): Long? = takeIf { it.isIntegralNumber }?.asLong()?.takeIf { it >= 0 }

    private fun JsonNode.stringOrNull(): String? = takeIf { it.isString }?.asString()

    private companion object {
        const val DATA = "data:"
        const val DONE = "[DONE]"
        const val CLOSED = "Closed by the gateway"
    }
}
