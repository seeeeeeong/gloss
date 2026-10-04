package gloss.common

class GlossException(
    val error: ErrorCode,
) : RuntimeException(error.code)
