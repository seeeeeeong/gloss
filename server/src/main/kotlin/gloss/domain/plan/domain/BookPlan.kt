package gloss.domain.plan.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.Entity
import org.hibernate.annotations.Immutable
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/**
 * What every post of a book is written from. Append-only: each post's prompt carries
 * [canonicalJson] byte for byte, and the provider's cache holds only a prefix that never changes.
 */
@Entity
@Immutable
class BookPlan private constructor(
    id: UUID,
    draft: BookPlanDraft,
    versionNo: Int,
    now: Instant,
) : BaseEntity(id) {
    val accountId: UUID = draft.accountId
    val bookId: UUID = draft.bookId
    val tocId: UUID = draft.tocId
    val versionNo: Int = versionNo
    val canonicalJson: String = draft.canonicalJson
    val jsonSha256: String = sha256(draft.canonicalJson)
    val model: String = draft.model
    val promptVersion: String = draft.promptVersion
    val createdAt: Instant = now

    companion object {
        fun create(
            draft: BookPlanDraft,
            versionNo: Int,
            now: Instant,
        ) = BookPlan(id = UuidV7.at(now), draft = draft, versionNo = versionNo, now = now)

        private fun sha256(text: String): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    }
}

/** A plan a model wrote and the policy accepted, as the bytes to keep. */
data class BookPlanDraft(
    val accountId: UUID,
    val bookId: UUID,
    val tocId: UUID,
    val canonicalJson: String,
    val model: String,
    val promptVersion: String,
)
