package gloss.domain.setup.domain

/** What a reader's list of sessions calls one: the book it holds or made, else what the reader first asked. */
object SetupTitlePolicy {
    // About a line of the list; the list cuts what still runs over.
    private const val MAX_LENGTH = 80
    private const val CUT = "…"

    fun of(
        heldTitle: String?,
        firstText: String?,
    ): String {
        if (heldTitle != null) return heldTitle
        val line =
            firstText
                .orEmpty()
                .lineSequence()
                .first()
                .trim()
        if (line.codePointCount(0, line.length) <= MAX_LENGTH) return line
        return line.substring(0, line.offsetByCodePoints(0, MAX_LENGTH)).trimEnd() + CUT
    }
}
