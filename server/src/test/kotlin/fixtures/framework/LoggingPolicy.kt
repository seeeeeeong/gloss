package fixtures.framework

import java.util.logging.Logger

/** Breaks the rule through java.util.logging, which sits under java.util. */
class LoggingPolicy(
    private val logger: Logger,
) {
    fun note() = logger.info("checked")
}
