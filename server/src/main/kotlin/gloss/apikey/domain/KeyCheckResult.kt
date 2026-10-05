package gloss.apikey.domain

sealed interface KeyCheckResult {
    data object Valid : KeyCheckResult

    /** The provider said the key is wrong. */
    data object Rejected : KeyCheckResult

    /** Nothing was learned about the key: a timeout, a rate limit or a server error. */
    data object Unavailable : KeyCheckResult
}
