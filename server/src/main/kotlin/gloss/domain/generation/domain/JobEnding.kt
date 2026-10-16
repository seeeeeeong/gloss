package gloss.domain.generation.domain

/** How an attempt ends its job, by the state it leaves in generation_job. */
sealed interface JobEnding {
    val state: String

    data object Succeeded : JobEnding {
        override val state = "succeeded"
    }

    data object Cancelled : JobEnding {
        override val state = "cancelled"
    }

    data class Failed(
        val errorKind: String,
    ) : JobEnding {
        override val state = "failed"
    }

    companion object {
        fun of(
            state: String,
            errorKind: String?,
        ): JobEnding =
            when (state) {
                Succeeded.state -> Succeeded
                Cancelled.state -> Cancelled
                else -> Failed(checkNotNull(errorKind) { "A failed job says why" })
            }
    }
}
