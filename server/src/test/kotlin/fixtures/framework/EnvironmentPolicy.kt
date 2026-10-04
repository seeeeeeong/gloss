package fixtures.framework

/** Breaks the rule through java.lang: it reads the process environment. */
class EnvironmentPolicy {
    fun limit(): String? = System.getenv("LIMIT")
}
