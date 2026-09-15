package ch.sumry.core.module.budget.dto

import ch.sumry.core.types.BudgetType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class BudgetProgressDTO(
    val categoryLabel: String,
    /** The configured limit for one period. */
    val limitAmount: BigDecimal,
    val spentAmount: BigDecimal,
    /** Against the effective limit, so rollover is already accounted for. */
    val remaining: BigDecimal,
    val percentage: Double,

    val budgetId: UUID,
    val categoryColor: String?,
    val period: BudgetType,
    val periodStart: LocalDate,
    /** Inclusive. */
    val periodEnd: LocalDate,
    val daysLeft: Int,
    val rollover: Boolean,
    /** What earlier periods handed over; negative when they overspent. Zero without rollover. */
    val carry: BigDecimal,
    val effectiveLimit: BigDecimal,
    /** under | on | over — spend so far against how much of the period has elapsed. */
    val pace: String,
    /** How much can still go per remaining day to land exactly on the limit. */
    val perDayLeft: BigDecimal,
)
