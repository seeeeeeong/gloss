package gloss.domain.llm.domain.chat

import gloss.domain.llm.domain.chat.ToolArgumentsParser.Problem
import gloss.domain.llm.domain.chat.ToolArgumentsParser.Result
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ToolArgumentsParserTest {
    private fun problemOf(raw: String): Problem? = (ToolArgumentsParser.parse(raw) as? Result.Malformed)?.problem

    @Test
    fun `arguments are read into strings, numbers, booleans, null, lists and maps`() {
        val raw =
            """ {"query": "운영체제 \"OS\"\n", "limit": 4, "ratio": 0.5, "big": 12345678901234567890,""" +
                """ "exact": true, "none": null, "tags": ["a", 1e2], "nested": {"k": []}} """

        assertThat(ToolArgumentsParser.parse(raw)).isEqualTo(
            Result.Parsed(
                mapOf(
                    "query" to "운영체제 \"OS\"\n",
                    "limit" to 4L,
                    "ratio" to BigDecimal("0.5"),
                    "big" to BigDecimal("12345678901234567890"),
                    "exact" to true,
                    "none" to null,
                    "tags" to listOf("a", BigDecimal("1e2")),
                    "nested" to mapOf("k" to emptyList<Any?>()),
                ),
            ),
        )
    }

    @Test
    fun `a function without parameters is called with an empty object`() {
        assertThat(ToolArgumentsParser.parse("{}")).isEqualTo(Result.Parsed(emptyMap()))
    }

    @Test
    fun `escapes are read, a unicode one included`() {
        val parsed = ToolArgumentsParser.parse("""{"s": "\u00e9\t\/\\"}""") as Result.Parsed

        assertThat(parsed.values["s"]).isEqualTo("é\t/\\")
    }

    @Test
    fun `arguments cut short, with text after them, or not JSON at all are malformed, with where reading stopped`() {
        assertThat(ToolArgumentsParser.parse("""{"query": "운영""")).isEqualTo(Result.Malformed(Problem.NOT_JSON, 13))
        assertThat(problemOf("""{"a": 1} {"b": 2}""")).isEqualTo(Problem.NOT_JSON)
        assertThat(problemOf("")).isEqualTo(Problem.NOT_JSON)
        assertThat(problemOf("""{query: "os"}""")).isEqualTo(Problem.NOT_JSON)
        assertThat(problemOf("""{"a": 1,}""")).isEqualTo(Problem.NOT_JSON)
        assertThat(problemOf("""{"a": 01}""")).isEqualTo(Problem.NOT_JSON)
        assertThat(problemOf("""{"a": tru}""")).isEqualTo(Problem.NOT_JSON)
        assertThat(problemOf("""{"a": "\x"}""")).isEqualTo(Problem.NOT_JSON)
        assertThat(problemOf("""{"a": "\u12"}""")).isEqualTo(Problem.NOT_JSON)
        assertThat(problemOf("""{"a": "\u-001"}""")).isEqualTo(Problem.NOT_JSON)
        assertThat(problemOf("{\"a\": \"line\nbreak\"}")).isEqualTo(Problem.NOT_JSON)
    }

    @Test
    fun `valid JSON that is not an object is not arguments`() {
        assertThat(problemOf("""["os"]""")).isEqualTo(Problem.NOT_AN_OBJECT)
        assertThat(problemOf(""""os"""")).isEqualTo(Problem.NOT_AN_OBJECT)
    }

    @Test
    fun `a key written twice is malformed rather than read either way`() {
        assertThat(
            ToolArgumentsParser.parse("""{"a": 1, "a": 2}"""),
        ).isEqualTo(Result.Malformed(Problem.DUPLICATE_KEY, 9))
    }

    @Test
    fun `nesting past any schema's depth is refused rather than followed`() {
        val deep = "{\"a\": " + "[".repeat(40) + "]".repeat(40) + "}"

        assertThat(problemOf(deep)).isEqualTo(Problem.TOO_DEEP)
    }

    @Test
    fun `a number JSON allows but no decimal can hold is malformed, not thrown`() {
        assertThat(ToolArgumentsParser.parse("""{"n": 1e999999999999}"""))
            .isEqualTo(Result.Malformed(Problem.NUMBER_OUT_OF_RANGE, 6))
        assertThat(problemOf("""{"n": -1E-999999999999}""")).isEqualTo(Problem.NUMBER_OUT_OF_RANGE)
    }

    @Test
    fun `a call's arguments parse through the call`() {
        val call = ToolCall(id = "call_0", name = "search_web", arguments = """{"query": "os"}""")

        assertThat(call.parsedArguments()).isEqualTo(Result.Parsed(mapOf("query" to "os")))
    }
}
