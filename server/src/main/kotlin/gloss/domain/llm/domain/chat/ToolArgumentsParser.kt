package gloss.domain.llm.domain.chat

import java.math.BigDecimal

/**
 * A tool call's arguments as one JSON object: strings, numbers (Long, or BigDecimal when not a
 * whole Long), booleans, null, lists and maps. Anything else is [Result.Malformed], never
 * an empty object, so a call the model wrote badly is still seen.
 */
object ToolArgumentsParser {
    sealed interface Result {
        data class Parsed(
            val values: Map<String, Any?>,
        ) : Result

        /** [at] is the character offset where reading stopped. */
        data class Malformed(
            val problem: Problem,
            val at: Int,
        ) : Result
    }

    enum class Problem {
        NOT_JSON,
        NOT_AN_OBJECT,
        DUPLICATE_KEY,
        TOO_DEEP,
        NUMBER_OUT_OF_RANGE,
    }

    // Far deeper than any tool's schema; bounds the recursion on text the model wrote.
    private const val MAX_DEPTH = 32

    fun parse(raw: String): Result =
        try {
            val reader = Reader(raw)
            when (val value = reader.document()) {
                is Map<*, *> -> Result.Parsed(value.entries.associate { (k, v) -> k.toString() to v })
                else -> Result.Malformed(Problem.NOT_AN_OBJECT, at = 0)
            }
        } catch (e: Unreadable) {
            Result.Malformed(e.problem, e.at)
        }

    private class Unreadable(
        val problem: Problem,
        val at: Int,
    ) : RuntimeException(problem.name) {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    private class Reader(
        private val text: String,
    ) {
        private var at = 0

        fun document(): Any? {
            val value = value(depth = 0)
            skipSpace()
            if (at != text.length) unreadable(at)
            return value
        }

        private fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw Unreadable(Problem.TOO_DEEP, at)
            skipSpace()
            return when (peek()) {
                '{' -> obj(depth)
                '[' -> list(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val members = LinkedHashMap<String, Any?>()
            at++
            skipSpace()
            if (take('}')) return members
            do {
                skipSpace()
                val keyAt = at
                if (peek() != '"') unreadable(at)
                val key = string()
                skipSpace()
                if (!take(':')) unreadable(at)
                if (members.containsKey(key)) throw Unreadable(Problem.DUPLICATE_KEY, keyAt)
                members[key] = value(depth + 1)
                skipSpace()
            } while (take(','))
            if (!take('}')) unreadable(at)
            return members
        }

        private fun list(depth: Int): List<Any?> {
            val items = mutableListOf<Any?>()
            at++
            skipSpace()
            if (take(']')) return items
            do {
                items += value(depth + 1)
                skipSpace()
            } while (take(','))
            if (!take(']')) unreadable(at)
            return items
        }

        private fun string(): String {
            val out = StringBuilder()
            at++
            while (true) {
                val c = peek() ?: unreadable(at)
                at++
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> out.append(escaped())
                    c < ' ' -> unreadable(at)
                    else -> out.append(c)
                }
            }
        }

        private fun escaped(): Char {
            val c = peek() ?: unreadable(at)
            at++
            return when (c) {
                '"', '\\', '/' -> {
                    c
                }

                'b' -> {
                    '\b'
                }

                'f' -> {
                    '\u000C'
                }

                'n' -> {
                    '\n'
                }

                'r' -> {
                    '\r'
                }

                't' -> {
                    '\t'
                }

                'u' -> {
                    val hex = text.substring(at, minOf(at + HEX_DIGITS, text.length))
                    if (!HEX.matches(hex)) unreadable(at)
                    at += HEX_DIGITS
                    hex.toInt(radix = 16).toChar()
                }

                else -> {
                    unreadable(at)
                }
            }
        }

        private fun literal(
            word: String,
            value: Any?,
        ): Any? {
            if (!text.startsWith(word, at)) unreadable(at)
            at += word.length
            return value
        }

        private fun number(): Number {
            val start = at
            val match = NUMBER.matchAt(text, at) ?: unreadable(at)
            at = match.range.last + 1
            val written = match.value
            val whole = match.groups[FRACTION] == null && match.groups[EXPONENT] == null
            return written.takeIf { whole }?.toLongOrNull()
                ?: decimalOrNull(written)
                ?: throw Unreadable(Problem.NUMBER_OUT_OF_RANGE, start)
        }

        private fun skipSpace() {
            while (peek()?.let { it in SPACE } == true) at++
        }

        private fun peek(): Char? = text.getOrNull(at)

        private fun take(c: Char): Boolean = (peek() == c).also { if (it) at++ }
    }

    // Valid JSON, but an exponent past BigDecimal's range, as 1e999999999999, has no value to give.
    @Suppress("SwallowedException")
    private fun decimalOrNull(written: String): BigDecimal? =
        try {
            BigDecimal(written)
        } catch (e: NumberFormatException) {
            null
        }

    private fun unreadable(at: Int): Nothing = throw Unreadable(Problem.NOT_JSON, at)

    private const val HEX_DIGITS = 4
    private val HEX = Regex("[0-9a-fA-F]{4}")
    private const val FRACTION = 1
    private const val EXPONENT = 2
    private val NUMBER = Regex("""-?(?:0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?""")
    private const val SPACE = " \t\n\r"
}
