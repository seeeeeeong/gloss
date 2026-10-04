package gloss.domain.account.entity

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import gloss.domain.account.entity.AccountRole
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant
import java.util.UUID

// Writes only changed columns, so a change never puts back a sign-in time it read before.
@Entity
@DynamicUpdate
class Account private constructor(
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

    companion object {
        /** Made from the environment at the first start; its password must be changed at first sign-in. */
        fun createFirstOperator(
            loginId: String,
            displayName: String,
            passwordHash: String,
            now: Instant,
        ) = Account(
            id = UuidV7.at(now),
            loginId = loginId,
            displayName = displayName,
            passwordHash = passwordHash,
            role = AccountRole.OPERATOR,
            mustChangePassword = true,
            createdBy = null,
            createdAt = now,
        )
    }
}
