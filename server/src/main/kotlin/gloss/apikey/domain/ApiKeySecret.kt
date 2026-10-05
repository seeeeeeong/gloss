package gloss.apikey.domain

/** A reader's API key as typed, trimmed. Only [of] makes one; printing it shows the last four. */
@JvmInline
value class ApiKeySecret private constructor(
    val value: String,
) {
    val last4: String get() = value.takeLast(LAST)

    override fun toString(): String = "ApiKeySecret(…$last4)"

    companion object {
        private const val LAST = 4

        // Loose on purpose: DeepSeek documents no key format, so the check call decides. Printable
        // ASCII without spaces fits a bearer token. 16: a 128-bit secret takes 22 characters even in
        // base64, so a shorter one is a cut-off paste. 200: far past any token and far under the
        // header sizes servers accept, so a longer one is pasted text, not a key.
        private val FORMAT = Regex("[\\x21-\\x7E]{16,200}")

        fun of(raw: String): ApiKeySecret? = raw.trim().takeIf { FORMAT.matches(it) }?.let(::ApiKeySecret)
    }
}
