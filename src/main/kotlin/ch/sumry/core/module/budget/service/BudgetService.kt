package ch.sumry.core.module.budget.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.budget.dto.BudgetCreateDTO
import ch.sumry.core.module.budget.dto.BudgetProgressDTO
import ch.sumry.core.module.budget.dto.BudgetResponseDTO
import ch.sumry.core.module.budget.dto.BudgetUpdateDTO
import ch.sumry.core.module.budget.model.Budget
import ch.sumry.core.module.budget.repo.BudgetRepository
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.types.Money
import ch.sumry.core.types.Period
import ch.sumry.core.types.money
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.Principal
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class BudgetService(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val userRepository: UserRepository
) {
    fun getAllFromPrincipal(principal: Principal): List<BudgetResponseDTO> =
        budgetRepository.findAllByUserId(UUID.fromString(principal.name)).map { toDto(it) }

    /**
     * Progress of every budget for the period containing [date].
     *
     * With rollover on, the limit for this period is the configured amount plus whatever
     * the earlier periods left over (or overspent), walked back to the budget's creation
     * and capped at two years so an old budget stays cheap to evaluate.
     */
    fun getBudgetProgress(userId: UUID, date: LocalDate): List<BudgetProgressDTO> {
        val today = LocalDate.now()
        return budgetRepository.findAllByUserId(userId).map { budget ->
            val categoryId = budget.category?.id!!
            val period = budget.period.periodContaining(date, budget.startDay)
            val spent = spentIn(userId, categoryId, period)

            val carry = if (budget.rollover) carryInto(userId, categoryId, budget, period) else Money.ZERO
            val effective = (budget.maxAmount + carry).money()
            val remaining = (effective - spent).money()
            val percentage = if (effective.signum() > 0)
                spent.divide(effective, 6, RoundingMode.HALF_UP).toDouble() * 100 else 100.0

            // Pace compares the share spent with the share of the period that has elapsed.
            val totalDays = ChronoUnit.DAYS.between(period.start, period.end).toInt().coerceAtLeast(1)
            val elapsedDays = when {
                today.isBefore(period.start) -> 0
                !today.isBefore(period.end) -> totalDays
                else -> ChronoUnit.DAYS.between(period.start, today).toInt() + 1
            }
            val daysLeft = totalDays - elapsedDays
            val elapsedShare = elapsedDays.toDouble() / totalDays
            val spentShare = if (effective.signum() > 0) spent.divide(effective, 6, RoundingMode.HALF_UP).toDouble() else 1.0
            val pace = when {
                spentShare > 1.0 -> "over"
                spentShare > elapsedShare + 0.05 -> "over"
                spentShare < elapsedShare - 0.05 -> "under"
                else -> "on"
            }
            val perDayLeft = if (daysLeft > 0 && remaining.signum() > 0)
                remaining.divide(BigDecimal(daysLeft), 2, RoundingMode.DOWN) else Money.ZERO

            BudgetProgressDTO(
                categoryLabel = budget.category?.label ?: "Unknown",
                limitAmount = budget.maxAmount,
                spentAmount = spent,
                remaining = remaining,
                percentage = percentage,
                budgetId = budget.id!!,
                categoryColor = budget.category?.color,
                period = budget.period,
                periodStart = period.start,
                periodEnd = period.lastDay,
                daysLeft = daysLeft,
                rollover = budget.rollover,
                carry = carry,
                effectiveLimit = effective,
                pace = pace,
                perDayLeft = perDayLeft,
            )
        }
    }

    private fun spentIn(userId: UUID, categoryId: UUID, period: Period): BigDecimal {
        val zone = ZoneId.systemDefault()
        val start = Date.from(period.start.atStartOfDay(zone).toInstant())
        // The repository query is BETWEEN (inclusive), so stop one millisecond before the next period.
        val end = Date(Date.from(period.end.atStartOfDay(zone).toInstant()).time - 1)
        return transactionRepository.getSpentAmountForCategory(userId, categoryId, start, end).money().abs()
    }

    private fun carryInto(userId: UUID, categoryId: UUID, budget: Budget, current: Period): BigDecimal {
        val created = budget.createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val firstPeriod = budget.period.periodContaining(created, budget.startDay)
        var carry = Money.ZERO
        var p = budget.period.previous(current)
        var steps = 0
        while (!p.start.isBefore(firstPeriod.start) && steps++ < MAX_ROLLOVER_PERIODS) {
            carry = (carry + budget.maxAmount - spentIn(userId, categoryId, p)).money()
            p = budget.period.previous(p)
        }
        return carry
    }

    @Transactional
    fun update(id: UUID, dto: BudgetUpdateDTO, principal: Principal): BudgetResponseDTO {
        val budget = owned(id, UUID.fromString(principal.name))
        dto.limitAmount?.let { budget.maxAmount = it.money() }
        dto.period?.let { budget.period = it }
        dto.rollover?.let { budget.rollover = it }
        dto.startDay?.let { budget.startDay = validStartDay(it) }
        return toDto(budgetRepository.save(budget))
    }

    @Transactional
    fun create(dto: BudgetCreateDTO, principal: Principal): BudgetResponseDTO {
        val userId = UUID.fromString(principal.name)
        val category = categoryRepository.findByLabelAndUser_IdAndIsActiveTrue(dto.categoryLabel, userId)
            .orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Category not found") }
        if (budgetRepository.existsByCategory_Id(category.id!!)) {
            throw RequestException(HttpStatus.CONFLICT, "Budget for this category already exists")
        }
        val budget = Budget(
            maxAmount = dto.maxAmount.money(),
            category = category,
            user = userRepository.getReferenceById(userId),
            period = dto.period,
            rollover = dto.rollover,
            startDay = validStartDay(dto.startDay),
        )
        return toDto(budgetRepository.save(budget))
    }

    @Transactional
    fun delete(id: UUID, principal: Principal) {
        budgetRepository.delete(owned(id, UUID.fromString(principal.name)))
    }

    private fun owned(id: UUID, userId: UUID): Budget {
        val budget = budgetRepository.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Budget not found") }
        if (budget.user?.id != userId) throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        return budget
    }

    private fun validStartDay(day: Int): Int {
        if (day !in 1..28) throw RequestException(HttpStatus.BAD_REQUEST, "startDay must be between 1 and 28")
        return day
    }

    private fun toDto(entity: Budget) = BudgetResponseDTO(
        id = entity.id!!,
        limitAmount = entity.maxAmount,
        categoryLabel = entity.category?.label!!,
        categoryColor = entity.category?.color,
        period = entity.period,
        rollover = entity.rollover,
        startDay = entity.startDay,
    )

    private companion object {
        const val MAX_ROLLOVER_PERIODS = 24
    }
}
