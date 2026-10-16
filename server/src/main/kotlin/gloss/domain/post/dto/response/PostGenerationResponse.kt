package gloss.domain.post.dto.response

/**
 * How far a section's post has come. [code] and [message] say why when it failed; the message is
 * shown as it is.
 */
data class PostGenerationResponse(
    val state: String,
    val code: String? = null,
    val message: String? = null,
) {
    companion object {
        /** The post is there to read. */
        val READY = PostGenerationResponse("ready")

        /** The book's plan is being written first; the post is asked for once it is kept. */
        val PLANNING = PostGenerationResponse("planning")

        val WRITING = PostGenerationResponse("writing")

        /** The plan found nothing to learn from this section, so it gets no post. */
        val NONE = PostGenerationResponse("none")

        fun failed(
            code: String,
            message: String,
        ) = PostGenerationResponse(state = "failed", code = code, message = message)
    }
}
