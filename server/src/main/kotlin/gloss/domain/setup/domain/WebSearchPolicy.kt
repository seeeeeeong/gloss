package gloss.domain.setup.domain

import java.time.Duration

/** Whether a web search on the operator's key may be asked, from the searches of the last [WINDOW]. */
object WebSearchPolicy {
    // Rolling, not a calendar day: what any 24 hours can spend is the cap, with no second burst at midnight.
    val WINDOW: Duration = Duration.ofHours(24)

    data class Caps(
        val perReader: Int,
        val perDay: Int,
    )

    fun decide(
        readerAsked: Int,
        allAsked: Int,
        caps: Caps,
    ): WebSearchDecision =
        when {
            allAsked >= caps.perDay -> WebSearchDecision.ALL_CAPPED
            readerAsked >= caps.perReader -> WebSearchDecision.READER_CAPPED
            else -> WebSearchDecision.ALLOWED
        }
}

enum class WebSearchDecision {
    ALLOWED,
    READER_CAPPED,
    ALL_CAPPED,
}
