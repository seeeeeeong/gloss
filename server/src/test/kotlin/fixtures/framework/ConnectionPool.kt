package fixtures.framework

import java.sql.Connection

/** Keeps the framework rule: it is not a rule class, so it may hold a connection. */
class ConnectionPool(
    private val connection: Connection,
) {
    fun close() = connection.close()
}
