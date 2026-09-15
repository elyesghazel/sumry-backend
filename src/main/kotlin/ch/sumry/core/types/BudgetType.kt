package ch.sumry.core.types

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A half-open window `[start, end)` of one budget period. */
data class Period(val start: LocalDate, val end: LocalDate) {
    val lastDay: LocalDate get() = end.minusDays(1)
    fun contains(d: LocalDate) = !d.isBefore(start) && d.isBefore(end)
}

enum class BudgetType {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    /**
     * The period containing [date]. Monthly periods can start on any day up to the 28th,
     * so a budget can run from payday to payday instead of calendar month to month.
     */
    fun periodContaining(date: LocalDate, startDay: Int = 1): Period = when (this) {
        DAILY -> Period(date, date.plusDays(1))
        WEEKLY -> {
            val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Period(start, start.plusWeeks(1))
        }
        MONTHLY -> {
            val day = startDay.coerceIn(1, 28)
            val start = if (date.dayOfMonth >= day) date.withDayOfMonth(day) else date.minusMonths(1).withDayOfMonth(day)
            Period(start, start.plusMonths(1))
        }
        YEARLY -> {
            val start = LocalDate.of(date.year, 1, 1)
            Period(start, start.plusYears(1))
        }
    }

    fun previous(period: Period): Period = when (this) {
        DAILY -> Period(period.start.minusDays(1), period.start)
        WEEKLY -> Period(period.start.minusWeeks(1), period.start)
        MONTHLY -> Period(period.start.minusMonths(1), period.start)
        YEARLY -> Period(period.start.minusYears(1), period.start)
    }
}
