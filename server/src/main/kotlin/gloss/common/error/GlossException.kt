package gloss.common.error

/** [cause] keeps what went wrong underneath for the log; the reader only sees [error]. */
class GlossException(
    val error: ErrorCode,
    cause: Throwable? = null,
) : RuntimeException(error.code, cause)
