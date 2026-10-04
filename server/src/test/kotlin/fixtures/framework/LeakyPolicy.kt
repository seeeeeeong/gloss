package fixtures.framework

import java.sql.Connection

/** Breaks the framework rule on purpose: a rule class must not reach a database. */
class LeakyPolicy(
    private val connection: Connection,
) {
    fun close() = connection.close()
}
