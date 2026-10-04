package gloss.domain.account.policy

object LoginIdPolicy {
    // Typed on a phone, shown in the operator's list; no @, so it is never taken for an email address.
    private val FORMAT = Regex("[a-z0-9][a-z0-9._-]{2,31}")

    fun normalize(raw: String): String? = raw.trim().lowercase().takeIf { FORMAT.matches(it) }
}
