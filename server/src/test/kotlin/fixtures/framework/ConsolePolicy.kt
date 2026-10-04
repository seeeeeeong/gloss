package fixtures.framework

/** Breaks the rule through kotlin.io: it reads standard input. */
class ConsolePolicy {
    fun answer(): String? = readlnOrNull()
}
