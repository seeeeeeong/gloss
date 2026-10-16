package gloss.domain.account.domain

import java.security.SecureRandom

/** What an operator hands a reader once, to be replaced at first sign-in. */
object TemporaryPasswordPolicy {
    // No 0/O, 1/l/I: an operator may read it out or a reader copy it by hand.
    private const val ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    // 20 of 57 symbols is about 116 bits, and passes the 15-character password policy.
    private const val LENGTH = 20

    private val random = SecureRandom()

    fun generate(): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}
