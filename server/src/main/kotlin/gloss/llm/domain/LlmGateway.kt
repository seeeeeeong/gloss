package gloss.llm.domain

/**
 * A streaming chat completion on the reader's own key. [onText] gets each piece as it comes and
 * must return promptly, handing it off rather than waiting on a slow client. [isCancelled] is
 * asked between pieces and, until the first, every second from another thread, so it must be
 * safe to call from any thread; true closes the connection.
 */
interface LlmGateway {
    fun stream(
        secret: String,
        request: ChatRequest,
        onText: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): ChatResult
}
