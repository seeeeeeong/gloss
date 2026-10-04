package gloss.account.domain

import gloss.account.AccountRole
import gloss.account.AccountSummary
import gloss.common.BaseEntity
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant
import java.util.UUID

// Writes only changed columns, so a change never puts back a sign-in time it read before.
@Entity
@DynamicUpdate
class Account(
    id: UUID,
    loginId: String,
    displayName: String,
    passwordHash: String,
    role: AccountRole,
    mustChangePassword: Boolean,
    createdBy: UUID?,
    createdAt: Instant,
) : BaseEntity(id) {
    var loginId: String = loginId
        protected set
    var displayName: String = displayName
        protected set
    var passwordHash: String = passwordHash
        protected set

    @Convert(converter = AccountRole.Column::class)
    var role: AccountRole = role
        protected set

    @Convert(converter = AccountStatus.Column::class)
    var status: AccountStatus = AccountStatus.ACTIVE
        protected set
    var mustChangePassword: Boolean = mustChangePassword
        protected set
    var sessionEpoch: Int = 0
        protected set
    var createdBy: UUID? = createdBy
        protected set
    var createdAt: Instant = createdAt
        protected set
    var passwordChangedAt: Instant? = null
        protected set
    var lastLoginAt: Instant? = null
        protected set
    var disabledAt: Instant? = null
        protected set

    val isActive: Boolean get() = status == AccountStatus.ACTIVE

    fun changePassword(
        hash: String,
        at: Instant,
    ) {
        passwordHash = hash
        mustChangePassword = false
        passwordChangedAt = at
        sessionEpoch += 1
    }

    fun toSummary() = AccountSummary(id, loginId, displayName, role, mustChangePassword)
}
