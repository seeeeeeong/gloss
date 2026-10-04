package fixtures.framework

import org.springframework.security.authorization.AuthorizationDecision

/** Breaks the rule through a framework type whose name only looks like a rule's. */
class ForeignDecisionPolicy {
    fun decide(): AuthorizationDecision = AuthorizationDecision(true)
}
