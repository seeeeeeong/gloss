package gloss.domain.setup.dto.response

/**
 * The contents a session holds, as text to edit and as a tree to read: an edition's, named by [ref]
 * and described by [book] as the source does now (null when it cannot be asked), or a book the reader
 * typed in, named by [title] and [authors] alone. An edition's [source] and [sourceUrl] are known
 * from its ref, so the credit its terms ask for is shown either way.
 */
data class SetupContentsResponse(
    val ref: String?,
    val source: String?,
    val sourceUrl: String?,
    val title: String,
    val authors: List<String>,
    val book: SetupEditionResponse?,
    val raw: String,
    val items: List<SetupContentsItemResponse>,
)
