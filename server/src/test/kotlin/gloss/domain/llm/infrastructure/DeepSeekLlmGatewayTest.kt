package gloss.domain.llm.infrastructure

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import gloss.domain.llm.domain.ChatRequest
import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.ReplyFormat
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.function.ThrowingSupplier
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DeepSeekLlmGatewayTest : DeepSeekFixture() {
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
        assertThat(sent.has("response_format")).isFalse()
    }

    @Test
    fun `a call for JSON asks the provider for its JSON output`() {
        answering {
            it.streams(chunk(finish = "stop"), usage, "data: [DONE]\n\n")
        }.stream(
            secret = "sk-reader",
            request = request.copy(format = ReplyFormat.JSON),
            onText = {},
            isCancelled = { false },
        )

        assertThat(
            json
                .readTree(body)
                .get("response_format")
                .get("type")
                .asString(),
        ).isEqualTo("json_object")
    }

    @Test
    fun `a refused key, an empty balance, a rate limit, a refused request and a failing provider each say which`() {
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
        assertThat(listOf(400, 403, 404, 422).map { failure(it).also { stop() }.kind })
            .containsOnly(LlmCallFailed.Kind.REFUSED_REQUEST)
        assertThat(failure(500).kind).isEqualTo(LlmCallFailed.Kind.PROVIDER_ERROR)
        stop()
        assertThat(
            failure(503),
        ).returns(LlmCallFailed.Kind.PROVIDER_ERROR, LlmCallFailed::kind).returns(false, LlmCallFailed::outputStarted)
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

    @Test
    fun `the background check and the stream's checks never ask the caller at once`() {
        val caller = Thread.currentThread()
        val backgroundAsking = AtomicBoolean(false)
        val backgroundAsked = CountDownLatch(1)
        val streamAsked = CountDownLatch(1)
        val overlapped = AtomicBoolean(false)
        val gateway =
            answering {
                // The stream opens while the background check is still asking.
                backgroundAsked.await(5, TimeUnit.SECONDS)
                it.streams(chunk("Hi"), chunk(finish = "stop"), usage, "data: [DONE]\n\n")
            }

        val result =
            gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = {
                if (Thread.currentThread() == caller) {
                    if (backgroundAsking.get()) overlapped.set(true)
                    streamAsked.countDown()
                } else {
                    backgroundAsking.set(true)
                    backgroundAsked.countDown()
                    streamAsked.await(2, TimeUnit.SECONDS)
                    backgroundAsking.set(false)
                }
                false
            })

        assertThat(result.finishReason).isEqualTo(FinishReason.STOP)
        assertThat(overlapped).isFalse()
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `a background check that fails as the answer finishes keeps the answer, and the failure is logged once`(
        output: CapturedOutput,
    ) {
        val caller = Thread.currentThread()
        val backgroundAsking = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val gateway =
            answering {
                // The answer finishes while the background check is still asking.
                backgroundAsking.await(5, TimeUnit.SECONDS)
                it.streams(chunk("Hi"), chunk(finish = "stop"), usage, "data: [DONE]\n\n")
            }

        val result =
            gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = {
                if (Thread.currentThread() != caller) {
                    backgroundAsking.countDown()
                    returned.await(5, TimeUnit.SECONDS)
                    error("session lookup failed")
                }
                false
            })
        returned.countDown()

        assertThat(result.finishReason).isEqualTo(FinishReason.STOP)
        await().atMost(Duration.ofSeconds(3)).until { LATE_FAILURE in output.out }
        assertThat(output.out).containsOnlyOnce(LATE_FAILURE).doesNotContain("session lookup failed")
    }

    @Test
    fun `a background check that never returns does not stop the stream reaching its deadline`() {
        val caller = Thread.currentThread()
        val backgroundAsked = CountDownLatch(1)
        val stuck = CountDownLatch(1)
        val gateway =
            answering(deadline = Duration.ofSeconds(2)) { exchange ->
                backgroundAsked.await(5, TimeUnit.SECONDS)
                exchange.streams(chunk("first"))
                while (runCatching { exchange.write(": keep-alive\n\n") }.isSuccess) {
                    stuck.await(100, TimeUnit.MILLISECONDS)
                }
            }

        try {
            val failure =
                assertTimeoutPreemptively(
                    Duration.ofSeconds(10),
                    ThrowingSupplier {
                        runCatching {
                            gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = {
                                if (Thread.currentThread() != caller) {
                                    backgroundAsked.countDown()
                                    stuck.await()
                                }
                                false
                            })
                        }.exceptionOrNull()
                    },
                )
            assertThat(failure).isInstanceOfSatisfying(LlmCallFailed::class.java) {
                assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
            }
        } finally {
            stuck.countDown()
        }
    }

    @Test
    fun `a cancel while the opened stream is still quiet is a cancel, not a dropped connection`() {
        val opened = CountDownLatch(1)
        val quiet = CountDownLatch(1)
        val gateway =
            answering(idleTimeout = Duration.ofSeconds(3)) { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.flush()
                opened.countDown()
                quiet.await(10, TimeUnit.SECONDS)
            }

        try {
            assertThatThrownBy {
                gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = {
                    opened.count == 0L
                })
            }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
                assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.CANCELLED)
            }
        } finally {
            quiet.countDown()
        }
    }

    private companion object {
        const val LATE_FAILURE = "A cancel check failed as the call ended: java.lang.IllegalStateException"
    }
}
