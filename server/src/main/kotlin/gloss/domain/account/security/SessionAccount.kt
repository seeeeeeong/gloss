package gloss.domain.account.security

import gloss.domain.account.entity.AccountRole
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.io.Serializable
import java.util.UUID

/** What a session remembers about who signed in. Spring Session serializes it into PostgreSQL. */
data class SessionAccount(
    val id: UUID,
    val role: AccountRole,
    val sessionEpoch: Int,
) : Serializable {
    fun authentication(): Authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            this,
            null,
            listOf(SimpleGrantedAuthority("ROLE_${role.name}")),
        )

    private companion object {
        // Sessions stored before a change to this class would otherwise fail to load.
        private const val serialVersionUID = 1L
    }
}
