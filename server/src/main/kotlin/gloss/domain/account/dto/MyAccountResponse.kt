package gloss.domain.account.dto

import gloss.domain.account.entity.Account
import java.util.UUID

data class MyAccountResponse(
    val id: UUID,
    val loginId: String,
    val displayName: String,
    val role: String,
    val mustChangePassword: Boolean,
) {
    companion object {
        fun from(account: Account) =
            MyAccountResponse(
                id = account.id,
                loginId = account.loginId,
                displayName = account.displayName,
                role = account.role.code,
                mustChangePassword = account.mustChangePassword,
            )
    }
}
