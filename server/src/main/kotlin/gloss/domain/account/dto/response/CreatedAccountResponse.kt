package gloss.domain.account.dto.response

/**
 * The temporary password appears here once, under Cache-Control: no-store, and is stored
 * nowhere but as a hash. The operator hands it to the reader.
 */
data class CreatedAccountResponse(
    val account: AdminAccountResponse,
    val temporaryPassword: String,
)
