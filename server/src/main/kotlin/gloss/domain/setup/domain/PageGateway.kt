package gloss.domain.setup.domain

/** Reads a web page's text for the model, from the server, where [PageAddressPolicy] allows. */
interface PageGateway {
    fun fetch(url: String): FetchedPage
}

/** A page as read: [url] is where it was found after any redirect, [text] as a browser would show it. */
data class FetchedPage(
    val url: String,
    val title: String?,
    val text: String,
)

/** A page that gave no text to use, and why, as the model is told. */
class PageFetchFailed(
    val kind: Kind,
) : RuntimeException(kind.name) {
    enum class Kind(
        val reason: String,
    ) {
        NOT_ALLOWED("only a public https page may be read"),
        TOO_LARGE("the page is too large to read"),
        NOT_TEXT("the page is not text or HTML"),
        TOO_MANY_REDIRECTS("the page redirects too many times"),
        FAILED("the page could not be read"),
    }

    private companion object {
        private const val serialVersionUID = 1L
    }
}
