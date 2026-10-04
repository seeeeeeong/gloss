package fixtures.framework

/** Holds a nested rule class that breaks the rule. */
class Container {
    class AccessPolicy(
        private val pool: ConnectionPool,
    ) {
        fun close() = pool.close()
    }
}
