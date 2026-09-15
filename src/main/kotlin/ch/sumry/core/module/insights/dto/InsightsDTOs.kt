package ch.sumry.core.module.insights.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class SubscriptionDTO(
    val name: String,
    /** WEEKLY | MONTHLY | QUARTERLY | YEARLY */
    val interval: String,
    val medianAmount: BigDecimal,
    val monthlyEquivalent: BigDecimal,
    val occurrences: Int,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val nextExpected: LocalDate,
    /** Seen recently enough that it is presumably still running. */
    val active: Boolean,
    val categoryLabel: String?,
    val categoryColor: String?,
    val accountId: UUID?,
    val accountName: String?,
    val paidLast12Months: BigDecimal,
    /** True when a planned transaction with the same name already exists. */
    val planned: Boolean,
)

data class SubscriptionsDTO(
    val subscriptions: List<SubscriptionDTO>,
    val activeCount: Int,
    val monthlyTotal: BigDecimal,
    val yearlyTotal: BigDecimal,
)

data class ForecastDTO(
    val monthStart: LocalDate,
    val monthEnd: LocalDate,
    val today: LocalDate,
    val daysElapsed: Int,
    val daysLeft: Int,

    val spentSoFar: BigDecimal,
    val incomeSoFar: BigDecimal,
    /** Planned expenses still due before the end of the month. */
    val plannedExpensesRemaining: BigDecimal,
    val plannedIncomeRemaining: BigDecimal,
    /** Median everyday spend per day over the last three full months, planned items excluded. */
    val baselineDailyPace: BigDecimal,
    /** The same figure for this month so far. */
    val currentDailyPace: BigDecimal,

    val projectedSpend: BigDecimal,
    val projectedIncome: BigDecimal,
    val projectedNet: BigDecimal,

    val lastMonthSpend: BigDecimal,
    val sameMonthLastYearSpend: BigDecimal,
    /** Projected spend against the last-three-months average: under | on | over */
    val verdict: String,
)
