package gloss.account.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.stereotype.Component

@Component
class SignedInSession(
    private val contexts: SecurityContextRepository,
    private val csrfTokens: CsrfTokenRepository,
) {
    /** A new session id and CSRF token, so neither one known before signing in is good after it. */
    fun start(
        account: SessionAccount,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): CsrfToken {
        remember(account, request, response)
        val token = csrfTokens.generateToken(request)
        csrfTokens.saveToken(token, request, response)
        return token
    }

    /** The same reader with a changed account, e.g. a new session epoch. The CSRF token stays. */
    fun remember(
        account: SessionAccount,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        request.getSession(true)
        request.changeSessionId()
        val context = SecurityContextHolder.createEmptyContext().apply { authentication = account.authentication() }
        SecurityContextHolder.setContext(context)
        contexts.saveContext(context, request, response)
    }

    fun csrfToken(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): CsrfToken =
        csrfTokens.loadToken(request)
            ?: csrfTokens.generateToken(request).also { csrfTokens.saveToken(it, request, response) }
}
