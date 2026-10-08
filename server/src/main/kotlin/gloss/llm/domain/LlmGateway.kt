package gloss.llm.domain

/**
 * A streaming chat completion on the reader's own key. [onText] gets each piece as it comes and
 * must return promptly, handing it off rather than waiting on a slow client. [isCancelled] is
 * asked at every line of the stream and every second from another thread: from any thread, but
 * never twice at once. True closes the connection.
 */
interface LlmGateway {
    fun stream(
        secret: String,
        request: ChatRequest,
        onText: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): ChatResult
}
