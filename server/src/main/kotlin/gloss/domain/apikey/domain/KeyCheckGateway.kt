package gloss.domain.apikey.domain

/** Asks the provider whether a key works, without running a model. */
interface KeyCheckGateway {
    fun check(secret: ApiKeySecret): KeyCheckResult
}

sealed interface KeyCheckResult {
    data object Valid : KeyCheckResult

    /** The provider said the key is wrong. */
    data object Rejected : KeyCheckResult

    /** Nothing was learned about the key: a timeout, a rate limit or a server error. */
    data object Unavailable : KeyCheckResult
}
