package gloss.domain.setup.domain

/** A web search engine, on the operator's key. [available] is false when no key is set. */
interface WebSearchGateway {
    val available: Boolean

    fun search(query: String): WebResults
}

/** [costMicros] is the engine's own estimate of what the search cost, when it gave one. */
data class WebResults(
    val requestId: String?,
    val costMicros: Long?,
    val pages: List<WebPage>,
)

/** A page found, with the passages of it that answer the query. */
data class WebPage(
    val url: String,
    val title: String,
    val published: String?,
    val highlights: List<String>,
)

/** A search that gave no answer to use. Never carries the key. */
class WebSearchFailed(
    val kind: Kind,
) : RuntimeException(kind.name) {
    enum class Kind {
        /** No key, or the engine refused it or has no credit left. */
        UNAVAILABLE,

        /** The engine's rate limit. */
        BUSY,

        /** No answer, or one that could not be read. */
        FAILED,
    }

    private companion object {
        private const val serialVersionUID = 1L
    }
}
