package gloss.generation.domain.job

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
