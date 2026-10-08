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
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The reader, driven through the gateway as a call drives it. */
class DeepSeekStreamReaderTest : DeepSeekFixture() {
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
    fun `a cut answer says so`() {
        val result =
            answering { it.streams(chunk("par"), chunk(finish = "length"), usage, "data: [DONE]\n\n") }
                .stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })

        assertThat(result.finishReason).isEqualTo(FinishReason.LENGTH)
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
    fun `a cancel check that throws a checked exception mid-stream is still the caller's failure`() {
        val gateway =
            answering { it.streams(chunk("Hel"), chunk("lo"), chunk(finish = "stop"), usage, "data: [DONE]\n\n") }
        var asked = 0
        val broken = IOException("session store unreachable")

        assertThatThrownBy {
            gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = {
                if (asked++ > 1) throw broken
                false
            })
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.CALLER_FAILED)
            assertThat(it.cause).isSameAs(broken)
        }
    }
}
