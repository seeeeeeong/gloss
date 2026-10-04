package gloss.account.security

import gloss.account.domain.AccountRepository
import gloss.common.ApiError
import gloss.common.writeApiError
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfException
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig {
    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    // A cookie rather than the session, so nobody gets a stored session before signing in.
    @Bean
    fun csrfTokenRepository(): CsrfTokenRepository =
        CookieCsrfTokenRepository().apply { setCookieCustomizer { it.sameSite("Lax") } }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        accounts: AccountRepository,
        json: ObjectMapper,
        contexts: SecurityContextRepository,
        csrfTokens: CsrfTokenRepository,
    ): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                it
                    .requestMatchers("/actuator/health", "/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.csrf {
                it.csrfTokenRepository(csrfTokens)
                // Unmasked: the masking handler would refuse the token rotated at sign-in.
                it.csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
            }.securityContext { it.securityContextRepository(contexts) }
            .requestCache { it.disable() }
            .logout {
                it.logoutUrl("/api/auth/logout")
                it.logoutSuccessHandler(HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
            }.exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.writeApiError(
                        json,
                        HttpStatus.UNAUTHORIZED,
                        ApiError("unauthorized:auth", "Sign in first"),
                    )
                }
                it.accessDeniedHandler { _, response, denied ->
                    val error =
                        if (denied is CsrfException) {
                            ApiError("invalid_csrf:auth", "A valid CSRF token is required")
                        } else {
                            ApiError("forbidden:auth", "Not allowed")
                        }
                    response.writeApiError(json, HttpStatus.FORBIDDEN, error)
                }
            }.addFilterBefore(AccountSessionFilter(accounts, json), AuthorizationFilter::class.java)
            .build()
}
