package gloss.generation.application

/** A handler's failure. [errorKind] is kept on the job; [retryable] asks for another attempt, if one is left. */
class JobFailed(
    val errorKind: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : RuntimeException(errorKind, cause)
