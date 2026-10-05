package gloss.apikey.domain

/** Asks the provider whether a key works, without running a model. */
interface KeyCheckGateway {
    fun check(secret: ApiKeySecret): KeyCheckResult
}
