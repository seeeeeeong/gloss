package gloss.common.security

import org.springframework.security.core.annotation.AuthenticationPrincipal

/** The signed-in [SessionAccount], as a controller parameter. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@AuthenticationPrincipal
annotation class CurrentAccount
