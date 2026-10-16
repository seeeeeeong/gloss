package gloss.domain.generation.domain.job

import java.util.UUID

data class NewJob(
    val accountId: UUID,
    val kind: JobKind,
    val dedupeKey: String,
    val payload: Map<String, String>,
)

enum class JobKind(
    val code: String,
) {
    PLAN("plan"),
    POST("post"),
    CHECK("check"),
    ;

    companion object {
        fun of(code: String): JobKind = entries.first { it.code == code }
    }
}
