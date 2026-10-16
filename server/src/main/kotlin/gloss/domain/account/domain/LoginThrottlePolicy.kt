package gloss.domain.account.domain

import java.time.Duration
import java.time.Instant

/**
 * Strict per login id and address, loose per address alone. No lock on a login id by itself:
 * anyone could then lock anyone else out by typing their id.
 */
object LoginThrottlePolicy {
    // A reader who mistyped waits a coffee break; a guesser gets five tries in that time.
    val WINDOW: Duration = Duration.ofMinutes(15)

    // Five guesses a quarter hour is ~480 a day: nothing against a 15-character password.
    const val PER_LOGIN_ID_AND_IP = 5

    // One office or carrier NAT shares an address; fifty leaves room for its readers.
    const val PER_IP = 50

    /**
     * Each list holds the failures inside the window, newest first, up to its limit. A block
     * lifts when the failure that filled a limit leaves the window, the later of the two if both.
     */
    fun decide(
        failuresForLoginIdAndIp: List<Instant>,
        failuresForIp: List<Instant>,
    ): LoginThrottleDecision {
        val until =
            listOfNotNull(
                failuresForLoginIdAndIp.getOrNull(PER_LOGIN_ID_AND_IP - 1),
                failuresForIp.getOrNull(PER_IP - 1),
            ).maxOrNull()
        return if (until == null) LoginThrottleDecision.Allowed else LoginThrottleDecision.Blocked(until.plus(WINDOW))
    }
}

sealed interface LoginThrottleDecision {
    data object Allowed : LoginThrottleDecision

    data class Blocked(
        val until: Instant,
    ) : LoginThrottleDecision
}
