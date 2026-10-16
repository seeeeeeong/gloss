package gloss.domain.account.domain

/** A login id as stored: trimmed and lowercased. Only [of] makes one, so every one is valid. */
@JvmInline
value class LoginId private constructor(
    val value: String,
) {
    companion object {
        // Typed on a phone, shown in the operator's list; no @, so it is never taken for an email address.
        private val FORMAT = Regex("[a-z0-9][a-z0-9._-]{2,31}")

        fun of(raw: String): LoginId? =
            raw
                .trim()
                .lowercase()
                .takeIf { FORMAT.matches(it) }
                ?.let(::LoginId)
    }
}
