package gloss.domain.account.policy

sealed interface LoginThrottleDecision {
    data object Allowed : LoginThrottleDecision

    data object Blocked : LoginThrottleDecision
}
