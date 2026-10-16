package gloss.domain.post.domain

import java.security.MessageDigest
import java.util.HexFormat

/** A post's Markdown, as the model wrote it. */
@JvmInline
value class PostContent private constructor(
    val value: String,
) {
    fun sha256(): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    companion object {
        // Same as post_version.content_md's check.
        const val MAX_LENGTH = 200_000

        // PostgreSQL text refuses NUL. Counted as char_length does, by code point.
        fun of(raw: String): PostContent? =
            raw
                .takeIf {
                    it.isNotBlank() && '\u0000' !in it && it.codePointCount(
                        0,
                        it.length,
                    ) <= MAX_LENGTH
                }?.let(::PostContent)
    }
}
