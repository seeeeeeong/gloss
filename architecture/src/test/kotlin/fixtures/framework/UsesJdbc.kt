package fixtures.framework

import java.sql.Connection

/** Breaks the framework rule on purpose: core must not reach a database. */
class UsesJdbc(
    private val connection: Connection,
) {
    fun close() = connection.close()
}
