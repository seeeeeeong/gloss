package gloss.domain.setup.dto.response

import java.time.LocalDate

/** An edition as the source describes it now, asked again to show a list: none of this is kept. */
data class SetupEditionResponse(
    val ref: String,
    val source: String,
    val url: String,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val publishedOn: LocalDate?,
    val kind: String?,
    val cover: String?,
    val hasContents: Boolean,
)
