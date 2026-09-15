package ch.sumry.core.module.planned.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Books auto-booking plans shortly after midnight, and once more mid-morning in case the
 * first run happened while the database was restarting. Both runs are idempotent: the
 * plan's watermark makes the second one a no-op when the first succeeded.
 */
@Component
class PlannedScheduler(private val service: PlannedService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 15 0,9 * * *", zone = "Europe/Zurich")
    fun run() {
        val booked = service.bookDueAutomatically()
        if (booked > 0) log.info("Auto-booked {} planned occurrence(s)", booked)
    }
}
