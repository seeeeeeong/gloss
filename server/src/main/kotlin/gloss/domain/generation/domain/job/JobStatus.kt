package gloss.domain.generation.domain.job

/** How a job stands. [errorKind] is why it failed, or why its last attempt did if it waits to run again. */
data class JobStatus(
    val state: JobState,
    val errorKind: String?,
)

enum class JobState(
    val code: String,
) {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    ;

    /** Waiting or running: its work is still to come, and asking for it again adds nothing. */
    val live: Boolean get() = this == QUEUED || this == RUNNING

    companion object {
        fun of(code: String): JobState = entries.first { it.code == code }
    }
}
