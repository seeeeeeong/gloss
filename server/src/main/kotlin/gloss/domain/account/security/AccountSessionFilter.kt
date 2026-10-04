package gloss.domain.account.security

import gloss.common.error.writeError
import gloss.domain.account.exception.AccountError
import gloss.domain.account.repository.AccountRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

/**
 * Ends a session whose account was disabled or whose password changed since, and keeps an
 * account that must change its password to that change.
 */
class AccountSessionFilter(
    private val accountRepository: AccountRepository,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? SessionAccount
        val account = principal?.let { accountRepository.findById(it.id).orElse(null) }
        when {
            principal == null -> {
                chain.doFilter(request, response)
            }

            account == null || !account.isActive || account.sessionEpoch != principal.sessionEpoch -> {
                // Carry on as anonymous: a protected path then answers 401, and signing in again works.
                request.getSession(false)?.invalidate()
                SecurityContextHolder.clearContext()
                chain.doFilter(request, response)
            }

            account.mustChangePassword && "${request.method} ${request.requestURI}" !in OPEN_UNTIL_CHANGED -> {
                response.writeError(objectMapper, AccountError.PASSWORD_CHANGE_REQUIRED)
            }

            else -> {
                chain.doFilter(request, response)
            }
        }
    }

    private companion object {
        val OPEN_UNTIL_CHANGED =
            setOf("GET /api/me", "GET /api/auth/csrf", "POST /api/auth/logout", "PUT /api/me/password")
    }
}
