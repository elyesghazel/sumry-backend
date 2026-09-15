package ch.sumry.core.module.budget.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.budget.dto.BudgetCreateDTO
import ch.sumry.core.module.budget.dto.BudgetProgressDTO
import ch.sumry.core.module.budget.dto.BudgetResponseDTO
import ch.sumry.core.module.budget.dto.BudgetUpdateDTO
import ch.sumry.core.module.budget.model.Budget
import ch.sumry.core.module.budget.repo.BudgetRepository
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.transaction.repo.TransactionRepository // Import sicherstellen
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.Principal
import java.time.YearMonth
import java.time.ZoneId
import java.util.*
import ch.sumry.core.types.Money
import ch.sumry.core.types.money

@Service
class BudgetService(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val userRepository: UserRepository
) {

    private fun getStartOfMonth(month: Int, year: Int): Date {
        val yearMonth = YearMonth.of(year, month)
        return Date.from(yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    private fun getEndOfMonth(month: Int, year: Int): Date {
        val yearMonth = YearMonth.of(year, month)
        return Date.from(yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant())
    }
    fun getAllFromUserId(userId: UUID, principal: Principal): List<Budget> {
        val authedUserId = UUID.fromString(principal.name)

        // Security Check
        if (authedUserId != userId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Access denied: You can only view your own budgets.")
        }

        return budgetRepository.findAllByUserId(userId)
    }

    fun getAllFromPrincipal(principal: Principal): List<BudgetResponseDTO> {
        return toDtoList(budgetRepository.findAllByUserId(UUID.fromString(principal.name)))
    }

    // only admins
    fun getAll(): List<Budget> = budgetRepository.findAll()

    fun getBudgetProgress(userId: UUID, month: Int, year: Int): List<BudgetProgressDTO> {
        val startOfMonth = getStartOfMonth(month, year)
        val endOfMonth = getEndOfMonth(month, year)

        val budgets = budgetRepository.findAllByUserId(userId)

        return budgets.map { budget ->
            // get spent amount from transaction repository
            val spentRaw = transactionRepository.getSpentAmountForCategory(
                userId,
                budget.category?.id!!, // categoryId has to be existent
                startOfMonth,
                endOfMonth
            ) ?: Money.ZERO

            val spentAmount = spentRaw.abs().money()

            val percentage = if (budget.maxAmount.signum() > 0) {
                spentAmount.divide(budget.maxAmount, 6, RoundingMode.HALF_UP).toDouble() * 100
            } else {
                100.0
            }

            BudgetProgressDTO(
                categoryLabel = budget?.category?.label ?: "Unknown",
                limitAmount = budget.maxAmount,
                spentAmount = spentAmount,
                remaining = (budget.maxAmount - spentAmount).money(),
                percentage = percentage
            )
        }
    }

    @Transactional
    fun update(id: UUID, dto: BudgetUpdateDTO, principal: Principal): BudgetResponseDTO {
        val userId = UUID.fromString(principal.name)

        // 1. Budget laden
        val budget = budgetRepository.findById(id).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "Budget not found")
        }

        // 2. Security Check: Gehört das Budget dem User?
        // Hinweis: Wir vergleichen Strings oder UUIDs sicherheitshalber
        if (budget.user?.id != userId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        }

        // 3. Änderungen anwenden
        if (dto.limitAmount != null) {
            budget.maxAmount = dto.limitAmount!!.money()
        }

        // 4. Speichern und zurückgeben
        val savedBudget = budgetRepository.save(budget)
        return toDto(savedBudget)
    }

    @Transactional
    fun create(dto: BudgetCreateDTO, principal: Principal): BudgetResponseDTO {
        val userId = UUID.fromString(principal.name)

        val category = categoryRepository.findByLabelAndUser_IdAndIsActiveTrue(dto.categoryLabel, userId)
            .orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Category not found") }

        if (budgetRepository.existsByCategory_Id(category.id!!)) {
            throw RequestException(HttpStatus.CONFLICT, "Budget for this category already exists")
        }

        val userRef = userRepository.getReferenceById(userId)

        val budget = Budget(maxAmount = dto.maxAmount.money(), category = category, user = userRef)
        val saved = budgetRepository.save(budget)

        return toDto(saved)
    }

    @Transactional
    fun delete(id: UUID, principal: Principal) {
        val userId = UUID.fromString(principal.name)

        val budget = budgetRepository.findById(id).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "Budget not found")
        }

        if (budget.user?.id != userId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        }

        budgetRepository.delete(budget)
    }

    // helper function
    private fun toDto(entity: Budget) = BudgetResponseDTO(
        id = entity.id!!,
        limitAmount = entity.maxAmount,
        categoryLabel = entity.category?.label!!,
        categoryColor = entity.category?.color
    )

    private fun toDtoList(entities: List<Budget>): List<BudgetResponseDTO> {
        return entities.map { item -> toDto(item) }
    }
}