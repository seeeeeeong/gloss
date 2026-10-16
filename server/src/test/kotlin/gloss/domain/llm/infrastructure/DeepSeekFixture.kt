package gloss.domain.llm.infrastructure

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import gloss.domain.llm.domain.ChatRequest
import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A DeepSeek stand-in on a local port, and the pieces of a stream it answers with. */
open class DeepSeekFixture {
    protected val json = JsonMapper()
    private var server: HttpServer? = null
    protected var body: String? = null
    protected var authorization: String? = null
    protected val request =
        ChatRequest(
            model = "deepseek-flash",
            messages = listOf(ChatMessage(Role.SYSTEM, "rules"), ChatMessage(Role.USER, "question")),
            maxTokens = 512,
            thinking = Thinking.DISABLED,
        )

    protected fun chunk(
        content: String? = null,
        finish: String? = null,
    ) = """data: {"choices":[{"index":0,"delta":{"content":${content?.let { "\"$it\"" } ?: "null"}},""" +
        """"finish_reason":${finish?.let { "\"$it\"" } ?: "null"}}],"usage":null}""" + "\n\n"

    protected val usage =
        """data: {"choices":[],"usage":{"prompt_tokens":30,"prompt_cache_hit_tokens":20,""" +
            """"prompt_cache_miss_tokens":10,"completion_tokens":7,"total_tokens":37,""" +
            """"completion_tokens_details":{"reasoning_tokens":3}}}""" + "\n\n"

    protected fun thought(text: String) =
        """data: {"choices":[{"index":0,"delta":{"reasoning_content":"$text"},"finish_reason":null}]}""" + "\n\n"

    protected fun answering(
        idleTimeout: Duration = Duration.ofSeconds(5),
        deadline: Duration = Duration.ofMinutes(1),
        handler: (HttpExchange) -> Unit,
    ): DeepSeekLlmGateway {
        val started =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/chat/completions") { exchange ->
                    body = exchange.requestBody.readAllBytes().decodeToString()
                    authorization = exchange.requestHeaders.getFirst("Authorization")
                    runCatching { handler(exchange) }
                    exchange.close()
                }
                executor =
                    java.util.concurrent.Executors
                        .newCachedThreadPool()
                start()
            }
        server = started
        return DeepSeekLlmGateway(
            baseUrl = "http://127.0.0.1:${started.address.port}",
            idleTimeout = idleTimeout,
            deadline = deadline,
        )
    }

    protected fun HttpExchange.streams(vararg parts: String) {
        responseHeaders.add("Content-Type", "text/event-stream")
        sendResponseHeaders(200, 0)
        parts.forEach {
            responseBody.write(it.toByteArray())
            responseBody.flush()
        }
    }

    // More of a stream already under way: the headers went with the first part.
    protected fun HttpExchange.write(part: String) {
        responseBody.write(part.toByteArray())
        responseBody.flush()
    }

    @AfterEach
    fun stop() {
        server?.stop(0)
    }
}
