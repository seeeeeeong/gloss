package gloss.llm.infrastructure

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import gloss.llm.domain.ChatRequest
import gloss.llm.domain.LlmCallFailed
import gloss.llm.domain.TokenUsage
import gloss.llm.domain.chat.ChatMessage
import gloss.llm.domain.chat.FinishReason
import gloss.llm.domain.chat.Role
import gloss.llm.domain.chat.Thinking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeepSeekLlmGatewayTest {
    private val json = JsonMapper()
    private var server: HttpServer? = null
    private var body: String? = null
    private var authorization: String? = null
    private val request =
        ChatRequest(
            model = "deepseek-flash",
            messages = listOf(ChatMessage(Role.SYSTEM, "rules"), ChatMessage(Role.USER, "question")),
            maxTokens = 512,
            thinking = Thinking.DISABLED,
        )

    private fun chunk(
        content: String? = null,
        finish: String? = null,
    ) = """data: {"choices":[{"index":0,"delta":{"content":${content?.let { "\"$it\"" } ?: "null"}},""" +
        """"finish_reason":${finish?.let { "\"$it\"" } ?: "null"}}],"usage":null}""" + "\n\n"

    private val usage =
        """data: {"choices":[],"usage":{"prompt_tokens":30,"prompt_cache_hit_tokens":20,""" +
            """"prompt_cache_miss_tokens":10,"completion_tokens":7,"total_tokens":37,""" +
            """"completion_tokens_details":{"reasoning_tokens":3}}}""" + "\n\n"

    private fun thought(text: String) =
        """data: {"choices":[{"index":0,"delta":{"reasoning_content":"$text"},"finish_reason":null}]}""" + "\n\n"

    private fun answering(
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

    private fun HttpExchange.streams(vararg parts: String) {
        responseHeaders.add("Content-Type", "text/event-stream")
        sendResponseHeaders(200, 0)
        parts.forEach {
            responseBody.write(it.toByteArray())
            responseBody.flush()
        }
    }

    // More of a stream already under way: the headers went with the first part.
    private fun HttpExchange.write(part: String) {
        responseBody.write(part.toByteArray())
        responseBody.flush()
    }

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    @Test
    fun `a stream is read piece by piece, with how it finished and what it used`() {
        val gateway =
            answering {
                it.streams(
                    ": keep-alive\n\n",
                    chunk("Hel"),
                    chunk("lo"),
                    chunk(finish = "stop"),
                    usage,
                    "data: [DONE]\n\n",
                )
            }
        val pieces = mutableListOf<String>()

        val result =
            gateway.stream(
                secret = "sk-reader",
                request = request,
                onText = { pieces += it },
                isCancelled = { false },
            )

        assertThat(pieces).containsExactly("Hel", "lo")
        assertThat(result.text).isEqualTo("Hello")
        assertThat(result.finishReason).isEqualTo(FinishReason.STOP)
        assertThat(
            result.usage,
        ).isEqualTo(TokenUsage(cacheHitTokens = 20, cacheMissTokens = 10, completionTokens = 7, reasoningTokens = 3))
    }

    @Test
    fun `every call says its model, cap and thinking, asks for usage, and carries the reader's key`() {
        answering {
            it.streams(
                chunk(finish = "stop"),
                usage,
                "data: [DONE]\n\n",
            )
        }.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })

        val sent = json.readTree(body)
        assertThat(authorization).isEqualTo("Bearer sk-reader")
        assertThat(sent.get("model").asString()).isEqualTo("deepseek-flash")
        assertThat(sent.get("max_tokens").asInt()).isEqualTo(512)
        assertThat(sent.get("thinking").get("type").asString()).isEqualTo("disabled")
        assertThat(sent.get("stream").asBoolean()).isTrue()
        assertThat(sent.get("stream_options").get("include_usage").asBoolean()).isTrue()
        assertThat(sent.get("messages").toList().map { it.get("role").asString() }).containsExactly("system", "user")
    }

    @Test
    fun `a cut answer says so`() {
        val result =
            answering { it.streams(chunk("par"), chunk(finish = "length"), usage, "data: [DONE]\n\n") }
                .stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })

        assertThat(result.finishReason).isEqualTo(FinishReason.LENGTH)
    }

    @Test
    fun `a refused key, an empty balance and a rate limit each say which`() {
        fun failure(status: Int) =
            runCatching {
                answering {
                    it.sendResponseHeaders(status, -1)
                }.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })
            }.exceptionOrNull() as LlmCallFailed

        assertThat(failure(401).kind).isEqualTo(LlmCallFailed.Kind.REJECTED_KEY)
        stop()
        assertThat(failure(402).kind).isEqualTo(LlmCallFailed.Kind.INSUFFICIENT_BALANCE)
        stop()
        assertThat(failure(429).kind).isEqualTo(LlmCallFailed.Kind.RATE_LIMITED)
        stop()
        assertThat(
            failure(503),
        ).returns(LlmCallFailed.Kind.PROVIDER_ERROR, LlmCallFailed::kind).returns(false, LlmCallFailed::outputStarted)
    }

    @Test
    fun `a connection dropped after some text is a network failure that may have billed`() {
        val gateway = answering { it.streams(chunk("par")) }

        assertThatThrownBy {
            gateway.stream(
                secret = "sk-reader",
                request = request,
                onText = {},
                isCancelled = { false },
            )
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
            assertThat(it.outputStarted).isTrue()
        }
    }

    @Test
    fun `a caller that cancels closes the connection, so the provider stops generating`() {
        val closed = CountDownLatch(1)
        val gateway =
            answering { exchange ->
                exchange.streams(chunk("first"))
                // The provider keeps writing until the socket goes away.
                repeat(1_000) {
                    if (runCatching { exchange.streams(chunk("more")) }.isFailure) {
                        closed.countDown()
                        return@answering
                    }
                    Thread.onSpinWait()
                }
            }
        var seen = 0

        assertThatThrownBy {
            gateway.stream(secret = "sk-reader", request = request, onText = { seen++ }, isCancelled = {
                seen >=
                    1
            })
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.CANCELLED)
            assertThat(it.outputStarted).isTrue()
        }
    }

    @Test
    fun `a provider that goes quiet past the idle timeout is given up on`() {
        val release = CountDownLatch(1)
        val gateway =
            answering(idleTimeout = Duration.ofMillis(300)) { exchange ->
                exchange.streams(chunk("first"))
                release.await(5, TimeUnit.SECONDS)
            }

        assertThatThrownBy {
            gateway.stream(
                secret = "sk-reader",
                request = request,
                onText = {},
                isCancelled = { false },
            )
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
        }
        release.countDown()
    }

    @Test
    fun `a connection dropped while the model was only thinking may have billed`() {
        val gateway = answering { it.streams(thought("let me see")) }

        assertThatThrownBy {
            gateway.stream(
                secret = "sk-reader",
                request = request,
                onText = {},
                isCancelled = { false },
            )
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
            assertThat(it.outputStarted).isTrue()
        }
    }

    @Test
    fun `a caller whose handler throws is told so, with what may have billed and why`() {
        val gateway =
            answering { it.streams(chunk("Hel"), chunk("lo"), chunk(finish = "stop"), usage, "data: [DONE]\n\n") }
        val broken = IllegalStateException("the socket to the browser closed")

        assertThatThrownBy {
            gateway.stream(secret = "sk-reader", request = request, onText = { throw broken }, isCancelled = { false })
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.CALLER_FAILED)
            assertThat(it.outputStarted).isTrue()
            assertThat(it.cause).isSameAs(broken)
        }
    }

    @Test
    fun `an event that is not json is the provider's error`() {
        val gateway = answering { it.streams(chunk("Hel"), "data: {not json\n\n") }

        assertThatThrownBy {
            gateway.stream(
                secret = "sk-reader",
                request = request,
                onText = {},
                isCancelled = { false },
            )
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.PROVIDER_ERROR)
            assertThat(it.outputStarted).isTrue()
        }
    }

    @Test
    fun `a call that keeps streaming past its deadline is given up on, however lively`() {
        val gateway =
            answering(deadline = Duration.ofMillis(300)) { exchange ->
                exchange.streams(chunk("first"))
                // Keep-alives forever: the idle timeout never trips, only the deadline can.
                while (runCatching { exchange.write(": keep-alive\n\n") }.isSuccess) Thread.onSpinWait()
            }

        assertThatThrownBy {
            gateway.stream(
                secret = "sk-reader",
                request = request,
                onText = {},
                isCancelled = { false },
            )
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
            assertThat(it.outputStarted).isTrue()
        }
    }

    @Test
    fun `a connection dropped after the request went out, before any answer, may have billed the prompt`() {
        // Reads the request, then goes away without a word.
        val gateway = answering { }

        assertThatThrownBy {
            gateway.stream(
                secret = "sk-reader",
                request = request,
                onText = {},
                isCancelled = { false },
            )
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
            assertThat(it.outputStarted).isFalse()
            assertThat(it.promptMayHaveBilled).isTrue()
        }
    }

    @Test
    fun `a refusal with a status bills nothing, nor does a request that never went out`() {
        val refused =
            runCatching {
                answering { it.sendResponseHeaders(503, -1) }
                    .stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })
            }.exceptionOrNull() as LlmCallFailed
        stop()
        val nowhere =
            DeepSeekLlmGateway(
                baseUrl = "http://127.0.0.1:1",
                idleTimeout = Duration.ofSeconds(1),
                deadline = Duration.ofSeconds(5),
            )
        val unsent =
            runCatching {
                nowhere.stream(
                    secret = "sk-reader",
                    request = request,
                    onText = {},
                    isCancelled = { false },
                )
            }.exceptionOrNull() as LlmCallFailed

        assertThat(refused.promptMayHaveBilled).isFalse()
        assertThat(unsent.promptMayHaveBilled).isFalse()
    }

    @Test
    fun `a cancel check that throws mid-stream is the caller's failure, with what may have billed`() {
        val gateway =
            answering { it.streams(chunk("Hel"), chunk("lo"), chunk(finish = "stop"), usage, "data: [DONE]\n\n") }
        var asked = 0
        val broken = IllegalStateException("session lookup failed")

        assertThatThrownBy {
            gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = {
                if (asked++ >
                    1
                ) {
                    throw broken
                } else {
                    false
                }
            })
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.CALLER_FAILED)
            assertThat(it.outputStarted).isTrue()
            assertThat(it.cause).isSameAs(broken)
        }
    }

    @Test
    fun `a stream cut after its last chunk, with how it finished and what it used, is complete`() {
        val result =
            answering { it.streams(chunk("Hello"), chunk(finish = "stop"), usage) }
                .stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })

        assertThat(result.text).isEqualTo("Hello")
        assertThat(result.finishReason).isEqualTo(FinishReason.STOP)
        assertThat(result.usage).isNotNull()
    }

    @Test
    fun `a request whose sending fails once connected may have billed its prompt`() {
        // Accepts, then hangs up at once, while a large prompt is still going out.
        val socket = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        Thread { runCatching { socket.accept().close() } }.start()
        val gateway =
            DeepSeekLlmGateway(
                baseUrl = "http://127.0.0.1:${socket.localPort}",
                idleTimeout = Duration.ofSeconds(5),
                deadline = Duration.ofSeconds(10),
            )
        val big = request.copy(messages = listOf(ChatMessage(Role.USER, "a".repeat(16_000_000))))

        try {
            assertThatThrownBy {
                gateway.stream(
                    secret = "sk-reader",
                    request = big,
                    onText = {},
                    isCancelled = { false },
                )
            }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
                assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
                assertThat(it.promptMayHaveBilled).isTrue()
            }
        } finally {
            socket.close()
        }
    }

    @Test
    fun `the deadline holds while the request is still being sent to a provider that never reads it`() {
        // Accepts and never reads: the socket's buffers fill and writing blocks.
        val socket = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val held = mutableListOf<java.net.Socket>()
        Thread { runCatching { held += socket.accept() } }.start()
        val gateway =
            DeepSeekLlmGateway(
                baseUrl = "http://127.0.0.1:${socket.localPort}",
                idleTimeout = Duration.ofSeconds(5),
                deadline = Duration.ofMillis(500),
            )
        val big = request.copy(messages = listOf(ChatMessage(Role.USER, "a".repeat(16_000_000))))

        try {
            assertThatThrownBy {
                gateway.stream(
                    secret = "sk-reader",
                    request = big,
                    onText = {},
                    isCancelled = { false },
                )
            }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
                assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
            }
        } finally {
            held.forEach { it.close() }
            socket.close()
        }
    }

    @Test
    fun `a call cancelled while waiting for the first answer is cut within a second, not at its deadline`() {
        val release = CountDownLatch(1)
        // Takes the request and thinks for ever before answering.
        val gateway = answering { release.await(30, TimeUnit.SECONDS) }

        try {
            assertThatThrownBy {
                gateway.stream(
                    secret = "sk-reader",
                    request = request,
                    onText = {},
                    isCancelled = { true },
                )
            }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
                assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.CANCELLED)
                assertThat(it.promptMayHaveBilled).isTrue()
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `a handler failing to write to its own client is the caller's failure, not the provider's`() {
        val gateway = answering { it.streams(chunk("Hel"), chunk(finish = "stop"), usage, "data: [DONE]\n\n") }
        val broken = java.io.IOException("broken pipe to the browser")

        assertThatThrownBy {
            gateway.stream(secret = "sk-reader", request = request, onText = { throw broken }, isCancelled = { false })
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.CALLER_FAILED)
            assertThat(it.cause).isSameAs(broken)
        }
    }

    @Test
    fun `usage missing a count is no usage, so the call is settled by estimate rather than as free`() {
        val partial =
            """data: {"choices":[],"usage":{"prompt_cache_hit_tokens":20,"prompt_cache_miss_tokens":10}}""" + "\n\n"

        val result =
            answering { it.streams(chunk("Hello"), chunk(finish = "stop"), partial, "data: [DONE]\n\n") }
                .stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })

        assertThat(result.usage).isNull()
    }

    @Test
    fun `a cancel check that throws before the first piece is the caller's failure, and the call is cut`() {
        val release = CountDownLatch(1)
        val gateway = answering { release.await(30, TimeUnit.SECONDS) }
        val broken = IllegalStateException("session lookup failed")

        try {
            assertThatThrownBy {
                gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { throw broken })
            }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
                assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.CALLER_FAILED)
                assertThat(it.cause).isSameAs(broken)
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `one call's stuck cancel check does not hold back another call's cancel`() {
        val entered = CountDownLatch(1)
        val stuck = CountDownLatch(1)
        // Neither call gets an answer, so only the background checks ask the callers.
        val gateway = answering(idleTimeout = Duration.ofSeconds(30)) { stuck.await(30, TimeUnit.SECONDS) }
        val first =
            Thread {
                runCatching {
                    gateway.stream(secret = "sk-a", request = request, onText = {}, isCancelled = {
                        entered.countDown()
                        stuck.await()
                        false
                    })
                }
            }.apply {
                isDaemon = true
                start()
            }

        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
            val reply =
                assertTimeoutPreemptively(
                    Duration.ofSeconds(10),
                    ThrowingSupplier {
                        runCatching {
                            gateway.stream(secret = "sk-b", request = request, onText = {}, isCancelled = { true })
                        }.exceptionOrNull()
                    },
                )
            assertThat(reply).isInstanceOfSatisfying(LlmCallFailed::class.java) {
                assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.CANCELLED)
            }
        } finally {
            stuck.countDown()
            first.join(5_000)
        }
    }

    @Test
    fun `a deadline that waits on a quiet stream does not hold back another call's deadline`() {
        val firstPiece = CountDownLatch(1)
        val stuck = CountDownLatch(1)
        val gateway =
            answering(idleTimeout = Duration.ofSeconds(30), deadline = Duration.ofSeconds(2)) { exchange ->
                // The first call goes quiet after a piece, so closing it waits on its blocked read.
                if (exchange.requestHeaders.getFirst("Authorization") == "Bearer sk-a") {
                    exchange.streams(chunk("first"))
                }
                stuck.await(30, TimeUnit.SECONDS)
            }
        val first =
            Thread {
                runCatching {
                    gateway.stream(
                        secret = "sk-a",
                        request = request,
                        onText = { firstPiece.countDown() },
                        isCancelled = { false },
                    )
                }
            }.apply {
                isDaemon = true
                start()
            }

        try {
            assertThat(firstPiece.await(5, TimeUnit.SECONDS)).isTrue()
            val failure =
                assertTimeoutPreemptively(
                    Duration.ofSeconds(10),
                    ThrowingSupplier {
                        runCatching {
                            gateway.stream(secret = "sk-b", request = request, onText = {}, isCancelled = { false })
                        }.exceptionOrNull()
                    },
                )
            assertThat(failure).isInstanceOfSatisfying(LlmCallFailed::class.java) {
                assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
            }
        } finally {
            stuck.countDown()
            first.join(5_000)
        }
    }
}
