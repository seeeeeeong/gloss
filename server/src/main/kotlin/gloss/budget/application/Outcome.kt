package gloss.budget.application

/** How a call ended, from the provider's finish_reason or from how it was cut off. */
enum class Outcome(
    val code: String,
) {
    STOP("stop"),
    LENGTH("length"),
    CONTENT_FILTER("content_filter"),
    CANCELLED_BEFORE_OUTPUT("cancelled_before_output"),
    CANCELLED_AFTER_OUTPUT("cancelled_after_output"),
    ERROR("error"),
}
