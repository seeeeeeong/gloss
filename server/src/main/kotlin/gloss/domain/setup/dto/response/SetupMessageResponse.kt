package gloss.domain.setup.dto.response

import gloss.domain.setup.domain.SetupContent
import gloss.domain.setup.domain.SetupKind
import gloss.domain.setup.domain.SetupMessage
import java.time.Instant

/** [payload]'s fields depend on [kind]. */
data class SetupMessageResponse(
    val seq: Int,
    val role: String,
    val kind: String,
    val payload: SetupContent,
    val createdAt: Instant,
) {
    companion object {
        fun from(message: SetupMessage): SetupMessageResponse {
            val kind = SetupKind.of(message.content)
            return SetupMessageResponse(
                seq = message.seq,
                role = kind.role.code,
                kind = kind.code,
                payload = message.content,
                createdAt = message.createdAt,
            )
        }
    }
}
