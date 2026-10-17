package gloss.domain.plan.dto.response

/**
 * How far the plan of a book's contents has come; [code] says why when it failed: the job's error,
 * `plan.cancelled` for a job cancelled before it wrote one, or `plan.not_started` for one never asked for.
 */
data class PlanStatusResponse(
    val state: String,
    val code: String? = null,
) {
    companion object {
        /** Asked for, waiting or being written: posts wait for it. */
        val PLANNING = PlanStatusResponse("planning")

        val READY = PlanStatusResponse("ready")

        fun failed(code: String) = PlanStatusResponse(state = "failed", code = code)
    }
}
