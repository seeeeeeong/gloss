package gloss.budget.domain

enum class LedgerKind(
    val code: String,
) {
    RESERVE("reserve"),
    SETTLE("settle"),
    RELEASE("release"),
    EXPIRE("expire"),
    ;

    companion object {
        fun of(code: String): LedgerKind = entries.first { it.code == code }
    }
}
