package gloss.book.domain

import java.security.MessageDigest
import java.text.Normalizer
import java.util.HexFormat
import java.util.Locale

/**
 * The key that names a section across versions of a table of contents, from its number and title.
 * Case, character width and spacing do not make a new key; a repeated one gets a suffix.
 */
object ItemKeyPolicy {
    // Any Unicode space or line separator, which NFKC leaves as it is.
    private val WHITESPACE = Regex("[\\s\\p{Z}]+")

    // Under toc_item.item_key's 600 with room for a suffix: NFKC can lengthen a title, as ½ to 1⁄2.
    private const val MAX_BASE = 580

    // A key cut to fit ends in a hash of all of it, so two that differ only past the cut stay apart:
    // 64 bits, so two thousand items in one version almost never collide (about 1 in 10^13).
    private const val HASH_LENGTH = 16
    private const val PATH = " > "

    /** [parent] is the place of the item's parent in the same list, which comes before it. */
    data class Entry(
        val number: String?,
        val title: String,
        val parent: Int?,
    )

    fun keysOf(entries: List<Entry>): List<String> {
        val used = HashSet<String>()
        val keys = ArrayList<String>(entries.size)
        for (entry in entries) {
            val own = escaped(listOfNotNull(entry.number?.let(::normalize), normalize(entry.title)).joinToString(" "))
            // A section is named by its path, so a heading repeated under another parent, as each
            // chapter's summary or each part's chapter 1, keeps its key when one is added elsewhere;
            // only a repeat under one parent counts in order. Renaming a parent renames what is under it.
            val parent = entry.parent
            val base = fitted(if (parent != null) keys[parent] + PATH + own else own)
            var key = base
            var n = 1
            while (!used.add(key)) key = "$base~${++n}"
            keys += key
        }
        return keys
    }

    // A title's own ">" or "~" is escaped, so "a > b" at the top never meets "b" under "a", and a
    // title "정리~2" never meets the second "정리".
    private fun escaped(name: String): String = name.replace("\\", "\\\\").replace(">", "\\>").replace("~", "\\~")

    private fun fitted(path: String): String {
        if (path.codePointCount(0, path.length) <= MAX_BASE) return path
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))
        val kept = path.substring(0, path.offsetByCodePoints(0, MAX_BASE - HASH_LENGTH - 1))
        return kept + "#" + HexFormat.of().formatHex(digest).take(HASH_LENGTH)
    }

    private fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}
