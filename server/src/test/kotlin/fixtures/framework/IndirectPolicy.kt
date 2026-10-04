package fixtures.framework

/** Breaks the rule through a project type that holds a connection. */
class IndirectPolicy(
    private val pool: ConnectionPool,
) {
    fun close() = pool.close()
}
