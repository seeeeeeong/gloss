package gloss.domain.apikey.service

import java.util.UUID

/** What another module may do with an account's key right now. */
sealed interface KeyAccess {
    /** The key in clear, for one call: never stored, logged or put in a job's payload. */
    data class Usable(
        val keyId: UUID,
        val secret: String,
    ) : KeyAccess {
        override fun toString(): String = "Usable(keyId=$keyId, secret=…${secret.takeLast(LAST)})"

        private companion object {
            const val LAST = 4
        }
    }

    data object Missing : KeyAccess

    /** Stored, but it no longer opens with the configured master keys; the reader saves it again. */
    data object Unreadable : KeyAccess

    /** The provider refused it on a call; the reader saves a working one. */
    data object Rejected : KeyAccess
}
