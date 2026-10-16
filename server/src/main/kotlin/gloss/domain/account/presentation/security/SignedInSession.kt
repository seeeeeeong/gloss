package gloss.domain.account.presentation.security

import gloss.common.security.SessionAccount
import gloss.domain.account.service.SignedInAccount
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.stereotype.Component

@Component
class SignedInSession(
    private val securityContextRepository: SecurityContextRepository,
    private val csrfTokenRepository: CsrfTokenRepository,
) {
    /** A new session id and CSRF token, so neither one known before signing in is good after it. */
    fun start(
        signedIn: SignedInAccount,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        remember(SessionAccount(signedIn.account.id, signedIn.role.authority, signedIn.sessionEpoch), request, response)
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response)
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
        securityContextRepository.saveContext(context, request, response)
    }

    fun csrfToken(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): CsrfToken =
        csrfTokenRepository.loadToken(request)
            ?: csrfTokenRepository.generateToken(request).also { csrfTokenRepository.saveToken(it, request, response) }
}
