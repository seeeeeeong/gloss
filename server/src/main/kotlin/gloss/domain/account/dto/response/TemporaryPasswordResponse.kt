package gloss.domain.account.dto.response

/** Shown once to the operator, under Cache-Control: no-store; stored only as a hash. */
data class TemporaryPasswordResponse(
    val temporaryPassword: String,
)
