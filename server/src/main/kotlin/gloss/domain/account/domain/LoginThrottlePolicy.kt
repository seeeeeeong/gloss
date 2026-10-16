package gloss.domain.account.domain

import java.time.Duration

/**
 * Strict per login id and address, loose per address alone. No lock on a login id by itself:
 * anyone could then lock anyone else out by typing their id.
 */
object LoginThrottlePolicy {
    // A reader who mistyped waits a coffee break; a guesser gets five tries in that time.
    val WINDOW: Duration = Duration.ofMinutes(15)

    // Five guesses a quarter hour is ~480 a day: nothing against a 15-character password.
    private const val PER_LOGIN_ID_AND_IP = 5

    // One office or carrier NAT shares an address; fifty leaves room for its readers.
    private const val PER_IP = 50

    fun decide(
        failuresForLoginIdAndIp: Int,
        failuresForIp: Int,
    ): LoginThrottleDecision =
        if (failuresForLoginIdAndIp >= PER_LOGIN_ID_AND_IP || failuresForIp >= PER_IP) {
            LoginThrottleDecision.Blocked
        } else {
            LoginThrottleDecision.Allowed
        }
}

sealed interface LoginThrottleDecision {
    data object Allowed : LoginThrottleDecision

    data object Blocked : LoginThrottleDecision
}
