package gloss.domain.budget.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Runs [ReservationExpiry] through its proxy, so each round is its own transaction. */
@Component
class ReservationExpiryJob(
    private val reservationExpiry: ReservationExpiry,
) {
    @Scheduled(
        fixedDelayString = "\${gloss.budget.expire-every}",
        initialDelayString = "\${gloss.budget.expire-first-after}",
    )
    fun run() {
        reservationExpiry.expireStale()
    }
}
