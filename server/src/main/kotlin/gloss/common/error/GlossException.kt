package gloss.common.error

class GlossException(
    val error: ErrorCode,
) : RuntimeException(error.code)
