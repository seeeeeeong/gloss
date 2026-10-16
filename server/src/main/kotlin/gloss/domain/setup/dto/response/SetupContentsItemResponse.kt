package gloss.domain.setup.dto.response

/** [ord] is the item's place in the whole list, from 0, and [parent] the ord of the item it sits under. */
data class SetupContentsItemResponse(
    val ord: Int,
    val parent: Int?,
    val depth: Int,
    val number: String?,
    val title: String,
)
