package fixtures.framework

import java.time.Duration

/** Keeps the rule: the standard library, java.time, another rule class and a value class. */
class PurePolicy(
    private val window: Duration,
) {
    fun allows(failures: Int): PureDecision =
        if (failures <
            window.toMinutes()
        ) {
            PureDecision.ALLOW
        } else {
            PureDecision.DENY
        }

    fun label(value: PureValue): String = value.text
}
