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
import gloss.domain.llm.domain.chat.ToolArgumentsParser
import gloss.domain.llm.domain.chat.ToolCall
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
    fun `a cancel that comes once the answer finished with its usage keeps the answer`() {
        val reading = Thread.currentThread()
        var asked = 0

        // Five lines go by before the usage arrives; the sixth check would be the first to cancel.
        val result =
            answering { it.streams(chunk("Hello"), chunk(finish = "stop"), usage, "data: [DONE]\n\n") }
                .stream(secret = "sk-reader", request = request, onText = {}, isCancelled = {
                    Thread.currentThread() == reading && asked++ >= 5
                })

        assertThat(result.text).isEqualTo("Hello")
        assertThat(result.finishReason).isEqualTo(FinishReason.STOP)
        assertThat(result.usage).isNotNull()
    }

    @Test
    fun `a cancel check that throws once the answer finished with its usage keeps the answer`() {
        val reading = Thread.currentThread()
        var asked = 0

        val result =
            answering { it.streams(chunk("Hello"), chunk(finish = "stop"), usage, "data: [DONE]\n\n") }
                .stream(secret = "sk-reader", request = request, onText = {}, isCancelled = {
                    check(Thread.currentThread() != reading || asked++ < 5) { "session lookup failed" }
                    false
                })

        assertThat(result.finishReason).isEqualTo(FinishReason.STOP)
        assertThat(result.usage).isNotNull()
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

    private fun calls(vararg fragments: String) =
        """data: {"choices":[{"index":0,"delta":{"tool_calls":[${fragments.joinToString(",")}]},""" +
            """"finish_reason":null}]}""" + "\n\n"

    // The first fragment of a call: its id and name, and no arguments yet.
    private fun opens(
        index: Int,
        id: String,
        name: String,
    ) = """{"index":$index,"id":"$id","type":"function","function":{"name":"$name","arguments":""}}"""

    // Every later fragment: only more of the arguments.
    private fun adds(
        index: Int,
        arguments: String,
    ) = """{"index":$index,"function":{"arguments":${json.writeValueAsString(arguments)}}}"""

    @Test
    fun `calls streamed side by side are put together by index, in index order, with how the turn ended`() {
        val pieces = mutableListOf<String>()

        val result =
            answering {
                it.streams(
                    calls(opens(1, "call_b", "find_books")),
                    calls(opens(0, "call_a", "search_web")),
                    calls(adds(0, "{\"que")),
                    calls(adds(1, "{\"title\""), adds(0, "ry\": \"운영")),
                    calls(adds(1, ": \"OS\"}")),
                    calls(adds(0, "체제\"}")),
                    chunk(finish = "tool_calls"),
                    usage,
                    "data: [DONE]\n\n",
                )
            }.stream(secret = "sk-reader", request = request, onText = { pieces += it }, isCancelled = { false })

        assertThat(result.finishReason).isEqualTo(FinishReason.TOOL_CALLS)
        assertThat(result.toolCalls).containsExactly(
            ToolCall(id = "call_a", name = "search_web", arguments = "{\"query\": \"운영체제\"}"),
            ToolCall(id = "call_b", name = "find_books", arguments = "{\"title\": \"OS\"}"),
        )
        assertThat(result.text).isEmpty()
        assertThat(pieces).isEmpty()
        assertThat(result.usage).isNotNull()
    }

    @Test
    fun `arguments split over many chunks come back whole, and a stream cut after its last chunk loses none`() {
        val arguments = "{\"query\": \"operating systems three easy pieces\"}"
        val fragments = arguments.chunked(3).map { calls(adds(0, it)) }.toTypedArray()

        val result =
            answering {
                it.streams(calls(opens(0, "call_a", "search_web")), *fragments, chunk(finish = "tool_calls"), usage)
            }.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })

        assertThat(result.toolCalls.single().arguments).isEqualTo(arguments)
        assertThat(result.toolCalls.single().parsedArguments())
            .isEqualTo(ToolArgumentsParser.Result.Parsed(mapOf("query" to "operating systems three easy pieces")))
    }

    @Test
    fun `arguments the model wrote badly come back as written, never dropped`() {
        val result =
            answering {
                it.streams(
                    calls(opens(0, "call_a", "search_web")),
                    calls(adds(0, "{\"query\": ")),
                    chunk(finish = "tool_calls"),
                    usage,
                    "data: [DONE]\n\n",
                )
            }.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })

        val call = result.toolCalls.single()
        assertThat(call.arguments).isEqualTo("{\"query\": ")
        assertThat(call.parsedArguments()).isInstanceOf(ToolArgumentsParser.Result.Malformed::class.java)
    }

    @Test
    fun `a call that never said its name is the provider's error, with what it billed`() {
        val gateway =
            answering {
                it.streams(
                    calls("""{"index":0,"id":"call_a","function":{"arguments":"{}"}}"""),
                    chunk(finish = "tool_calls"),
                    usage,
                    "data: [DONE]\n\n",
                )
            }

        assertThatThrownBy {
            gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.PROVIDER_ERROR)
            assertThat(it.outputStarted).isTrue()
            assertThat(it.usage).isNotNull()
        }
    }

    @Test
    fun `a turn that ended for tool calls without any is the provider's error`() {
        val gateway = answering { it.streams(chunk(finish = "tool_calls"), usage, "data: [DONE]\n\n") }

        assertThatThrownBy {
            gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.PROVIDER_ERROR)
        }
    }

    @Test
    fun `a fragment without its index is the provider's error`() {
        val gateway = answering { it.streams(calls("""{"id":"call_a","function":{"name":"search_web"}}""")) }

        assertThatThrownBy {
            gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.PROVIDER_ERROR)
            assertThat(it.outputStarted).isTrue()
        }
    }

    @Test
    fun `a connection dropped mid-call may have billed`() {
        val gateway = answering { it.streams(calls(opens(0, "call_a", "search_web"))) }

        assertThatThrownBy {
            gateway.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })
        }.isInstanceOfSatisfying(LlmCallFailed::class.java) {
            assertThat(it.kind).isEqualTo(LlmCallFailed.Kind.NETWORK)
            assertThat(it.outputStarted).isTrue()
        }
    }

    @Test
    fun `the thinking is kept beside the answer, for a later call to send back`() {
        val result =
            answering {
                it.streams(
                    thought("let "),
                    thought("me see"),
                    chunk("Hi"),
                    chunk(finish = "stop"),
                    usage,
                    "data: [DONE]\n\n",
                )
            }.stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })

        assertThat(result.reasoning).isEqualTo("let me see")
        assertThat(result.text).isEqualTo("Hi")
        assertThat(result.toolCalls).isEmpty()
    }
}
