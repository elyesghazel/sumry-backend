package ch.sumry.core.module.insights.service

import ch.sumry.core.module.insights.dto.ForecastDTO
import ch.sumry.core.module.insights.dto.SubscriptionDTO
import ch.sumry.core.module.insights.dto.SubscriptionsDTO
import ch.sumry.core.module.planned.repo.PlannedRepository
import ch.sumry.core.module.planned.service.PlannedService
import ch.sumry.core.module.transaction.model.Transaction
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.types.Money
import ch.sumry.core.types.TransactionKind
import ch.sumry.core.types.money
import ch.sumry.core.types.orZero
import ch.sumry.core.types.sumMoney
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.Principal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.abs

@Service
class InsightsService(
    private val transactions: TransactionRepository,
    private val planned: PlannedRepository,
    private val plannedService: PlannedService,
) {
    private val zone: ZoneId = ZoneId.systemDefault()

    // ---------------------------------------------------------- subscriptions

    /**
     * Finds things that get paid again and again at a steady interval for a steady amount.
     *
     * Notes are normalised (case, digits, punctuation) so "Swisscom 09/2026" and
     * "Swisscom 10/2026" group together. A group qualifies when its gaps cluster around a
     * known interval and its amounts cluster around their median.
     */
    fun subscriptions(principal: Principal): SubscriptionsDTO {
        val userId = UUID.fromString(principal.name)
        val today = LocalDate.now()
        val since = today.minusMonths(15)
        val rows = transactions.findByUserAccount_User_Id(userId)
            .filter { it.effectiveKind() == TransactionKind.EXPENSE && it.date != null && localDate(it).isAfter(since) }
        val plannedNames = planned.findByUser_IdAndIsActiveTrueOrderByStartDateAsc(userId)
            .mapNotNull { it.name?.let { n -> normalise(n) } }.toSet()

        val groups = rows.groupBy { normalise(it.description ?: "") }.filterKeys { it.isNotBlank() }
        val found = ArrayList<SubscriptionDTO>()
        for ((key, txs) in groups) {
            val sorted = txs.sortedBy { it.date }
            if (sorted.size < 2) continue
            val dates = sorted.map { localDate(it) }
            val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.filter { it > 0 }
            if (gaps.isEmpty()) continue
            val medianGap = median(gaps.map { it.toDouble() })
            val interval = intervalFor(medianGap) ?: continue
            val minOccurrences = if (interval == "YEARLY") 2 else 3
            if (sorted.size < minOccurrences) continue

            // Steadiness: most gaps close to the median, most amounts close to theirs.
            val tolerance = maxOf(4.0, medianGap * 0.2)
            val steadyGaps = gaps.count { abs(it - medianGap) <= tolerance }.toDouble() / gaps.size
            if (steadyGaps < 0.6) continue
            val amounts = sorted.map { it.amount.orZero().toDouble() }
            val medianAmount = median(amounts)
            val steadyAmounts = amounts.count { abs(it - medianAmount) <= medianAmount * 0.15 + 0.5 }.toDouble() / amounts.size
            if (steadyAmounts < 0.6) continue

            val last = dates.last()
            val next = last.plusDays(medianGap.toLong())
            val perMonth = when (interval) { "WEEKLY" -> 4.348; "MONTHLY" -> 1.0; "QUARTERLY" -> 1.0 / 3; else -> 1.0 / 12 }
            val name = sorted.groupingBy { it.description?.trim() ?: key }.eachCount().maxByOrNull { it.value }!!.key
            val category = sorted.mapNotNull { it.category }.groupingBy { it.id }.eachCount().maxByOrNull { it.value }?.key
                ?.let { id -> sorted.first { it.category?.id == id }.category }
            val account = sorted.last().userAccount
            found += SubscriptionDTO(
                name = name,
                interval = interval,
                medianAmount = Money.of(medianAmount),
                monthlyEquivalent = Money.of(medianAmount * perMonth),
                occurrences = sorted.size,
                firstDate = dates.first(),
                lastDate = last,
                nextExpected = next,
                active = ChronoUnit.DAYS.between(last, today) <= medianGap * 1.5 + 3,
                categoryLabel = category?.label,
                categoryColor = category?.color,
                accountId = account?.id,
                accountName = account?.accountName,
                paidLast12Months = sorted.filter { localDate(it).isAfter(today.minusYears(1)) }.map { it.amount }.sumMoney(),
                planned = key in plannedNames,
            )
        }
        val list = found.sortedWith(compareByDescending<SubscriptionDTO> { it.active }.thenByDescending { it.monthlyEquivalent })
        val active = list.filter { it.active }
        val monthly = active.map { it.monthlyEquivalent }.sumMoney()
        return SubscriptionsDTO(
            subscriptions = list,
            activeCount = active.size,
            monthlyTotal = monthly,
            yearlyTotal = monthly.multiply(BigDecimal(12)).money(),
        )
    }

    private fun intervalFor(gap: Double): String? = when (gap) {
        in 6.0..8.0 -> "WEEKLY"
        in 26.0..35.0 -> "MONTHLY"
        in 84.0..100.0 -> "QUARTERLY"
        in 350.0..380.0 -> "YEARLY"
        else -> null
    }

    // ---------------------------------------------------------------- forecast

    /**
     * Where this month is heading: what has gone out, what is still committed, and the
     * everyday spend that will happen on top at the recent pace.
     */
    fun forecast(principal: Principal, at: LocalDate?): ForecastDTO {
        val userId = UUID.fromString(principal.name)
        val today = at ?: LocalDate.now()
        val month = YearMonth.from(today)
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val all = transactions.findByUserAccount_User_Id(userId).filter { it.date != null && !it.effectiveKind().isTransfer }
        val plannedNames = planned.findByUser_IdOrderByStartDateAsc(userId)
            .flatMap { listOfNotNull(it.name, it.description) }.map { normalise(it) }.filter { it.isNotBlank() }.toSet()

        fun inMonth(m: YearMonth) = all.filter { YearMonth.from(localDate(it)) == m }
        fun spent(txs: List<Transaction>) = txs.filter { it.effectiveKind() == TransactionKind.EXPENSE }.map { it.amount }.sumMoney()
        fun everyday(txs: List<Transaction>) = txs.filter {
            it.effectiveKind() == TransactionKind.EXPENSE && normalise(it.description ?: "") !in plannedNames
        }.map { it.amount }.sumMoney()

        val thisMonth = inMonth(month).filter { !localDate(it).isAfter(today) }
        val spentSoFar = spent(thisMonth)
        val incomeSoFar = thisMonth.filter { it.effectiveKind() == TransactionKind.INCOME }.map { it.amount }.sumMoney()
        val daysElapsed = today.dayOfMonth
        val daysLeft = month.lengthOfMonth() - daysElapsed

        // Baseline pace: median of the last three full months' everyday spend per day.
        val paces = (1..3).map { i ->
            val m = month.minusMonths(i.toLong())
            everyday(inMonth(m)).toDouble() / m.lengthOfMonth()
        }
        val baseline = Money.of(median(paces))
        val currentPace = everyday(thisMonth).divide(BigDecimal(daysElapsed), 2, RoundingMode.HALF_UP)

        val upcoming = plannedService.upcoming(principal, ChronoUnit.DAYS.between(today, end).toInt().coerceAtLeast(1), null)
            .filter { !it.date.isAfter(end) && !it.overdue }
        val plannedExpenses = upcoming.filter { it.kind == TransactionKind.EXPENSE }.map { it.amount }.sumMoney()
        val plannedIncome = upcoming.filter { it.kind == TransactionKind.INCOME }.map { it.amount }.sumMoney()

        val projectedSpend = (spentSoFar + plannedExpenses + baseline.multiply(BigDecimal(daysLeft))).money()
        val projectedIncome = (incomeSoFar + plannedIncome).money()
        val lastMonth = spent(inMonth(month.minusMonths(1)))
        val lastYear = spent(inMonth(month.minusYears(1)))
        val recentAverage = (1..3).map { spent(inMonth(month.minusMonths(it.toLong()))) }.sumMoney()
            .divide(BigDecimal(3), 2, RoundingMode.HALF_UP)
        val verdict = when {
            recentAverage.signum() == 0 -> "on"
            projectedSpend > recentAverage.multiply(BigDecimal("1.10")) -> "over"
            projectedSpend < recentAverage.multiply(BigDecimal("0.90")) -> "under"
            else -> "on"
        }
        return ForecastDTO(
            monthStart = start,
            monthEnd = end,
            today = today,
            daysElapsed = daysElapsed,
            daysLeft = daysLeft,
            spentSoFar = spentSoFar,
            incomeSoFar = incomeSoFar,
            plannedExpensesRemaining = plannedExpenses,
            plannedIncomeRemaining = plannedIncome,
            baselineDailyPace = baseline,
            currentDailyPace = currentPace,
            projectedSpend = projectedSpend,
            projectedIncome = projectedIncome,
            projectedNet = (projectedIncome - projectedSpend).money(),
            lastMonthSpend = lastMonth,
            sameMonthLastYearSpend = lastYear,
            verdict = verdict,
        )
    }

    // ----------------------------------------------------------------- helpers

    private fun localDate(t: Transaction): LocalDate = t.date!!.toInstant().atZone(zone).toLocalDate()

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 0) (s[mid - 1] + s[mid]) / 2 else s[mid]
    }

    companion object {
        private val NOISE = Regex("[\\d\\p{Punct}]+")
        private val SPACES = Regex("\\s+")

        /** Lowercase, digits and punctuation stripped, first three words — the identity of a payee. */
        fun normalise(text: String): String =
            text.lowercase().replace(NOISE, " ").replace(SPACES, " ").trim().split(' ').take(3).joinToString(" ")
    }
}
