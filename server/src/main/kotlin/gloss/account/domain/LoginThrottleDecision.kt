package gloss.account.domain

sealed interface LoginThrottleDecision {
    data object Allowed : LoginThrottleDecision

    data object Blocked : LoginThrottleDecision
}
