package fixtures.framework

/** A value class reading the environment: no rule name, but a value class is held to the same rule. */
@JvmInline
value class LeakyValue(
    val text: String,
) {
    fun withHome(): String = text + System.getenv("HOME")
}
