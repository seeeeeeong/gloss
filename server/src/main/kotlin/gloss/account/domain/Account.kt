package gloss.account.domain

import gloss.account.domain.AccountRole
import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
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

    /** A reset hands out a temporary password: it must be changed, and every session ends. */
    fun resetPassword(
        temporaryHash: String,
        at: Instant,
    ) {
        passwordHash = temporaryHash
        mustChangePassword = true
        passwordChangedAt = at
        sessionEpoch += 1
    }

    /** False when already disabled: nothing changes, so nothing is to be recorded. */
    fun disable(at: Instant): Boolean {
        if (!isActive) return false
        status = AccountStatus.DISABLED
        disabledAt = at
        sessionEpoch += 1
        return true
    }

    /** False when already active. */
    fun enable(): Boolean {
        if (isActive) return false
        status = AccountStatus.ACTIVE
        disabledAt = null
        return true
    }

    fun rename(displayName: String) {
        this.displayName = displayName
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

        /** Made by an operator with a temporary password, which must be changed at first sign-in. */
        fun createByOperator(
            draft: AccountDraft,
            temporaryHash: String,
            createdBy: UUID,
            now: Instant,
        ) = Account(
            id = UuidV7.at(now),
            loginId = draft.loginId,
            displayName = draft.displayName,
            passwordHash = temporaryHash,
            role = draft.role,
            mustChangePassword = true,
            createdBy = createdBy,
            createdAt = now,
        )
    }
}
