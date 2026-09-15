package ch.sumry.core.module.goal.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.goal.dto.*
import ch.sumry.core.module.goal.model.Goal
import ch.sumry.core.module.goal.repo.GoalRepository
import ch.sumry.core.module.transaction.dto.TransferCreateDTO
import ch.sumry.core.module.transaction.service.TransactionService
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.types.Money
import ch.sumry.core.types.money
import ch.sumry.core.types.orZero
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
class GoalService(
    private val repo: GoalRepository,
    private val accounts: UserAccountRepository,
    private val users: UserRepository,
    private val transactions: TransactionService,
) {
    fun list(principal: Principal, includeInactive: Boolean): List<GoalResponseDTO> {
        val userId = UUID.fromString(principal.name)
        val rows = if (includeInactive) repo.findByUser_IdOrderByCreatedAtAsc(userId) else repo.findByUser_IdAndIsActiveTrueOrderByCreatedAtAsc(userId)
        return rows.map { toDto(it) }
    }

    @Transactional
    fun create(dto: GoalCreateDTO, principal: Principal): GoalResponseDTO {
        val userId = UUID.fromString(principal.name)
        val goal = Goal(
            user = users.getReferenceById(userId),
            name = dto.name.trim(),
            targetAmount = dto.targetAmount.money(),
            targetDate = dto.targetDate,
            account = dto.accountId?.let { ownedAccount(it, userId) },
            savedAmount = dto.savedAmount?.money() ?: Money.ZERO,
            color = dto.color,
            icon = dto.icon,
        )
        return toDto(repo.save(markAchieved(goal)))
    }

    @Transactional
    fun update(id: UUID, dto: GoalUpdateDTO, principal: Principal): GoalResponseDTO {
        val userId = UUID.fromString(principal.name)
        val goal = owned(id, userId)
        dto.name?.let { goal.name = it.trim() }
        dto.targetAmount?.let {
            if (it.signum() <= 0) throw RequestException(HttpStatus.BAD_REQUEST, "targetAmount must be positive")
            goal.targetAmount = it.money()
        }
        if (dto.clearTargetDate) goal.targetDate = null else dto.targetDate?.let { goal.targetDate = it }
        if (dto.clearAccount) goal.account = null else dto.accountId?.let { goal.account = ownedAccount(it, userId) }
        dto.savedAmount?.let { goal.savedAmount = it.money() }
        dto.color?.let { goal.color = it }
        dto.icon?.let { goal.icon = it }
        dto.isActive?.let { goal.isActive = it }
        return toDto(repo.save(markAchieved(goal)))
    }

    @Transactional
    fun delete(id: UUID, principal: Principal) = repo.delete(owned(id, UUID.fromString(principal.name)))

    /**
     * Adds to the goal. A linked goal with a source account books a real transfer so the
     * money actually moves; an unlinked goal just raises its own counter.
     */
    @Transactional
    fun contribute(id: UUID, dto: GoalContributeDTO, principal: Principal): GoalResponseDTO {
        val userId = UUID.fromString(principal.name)
        val goal = owned(id, userId)
        val target = goal.account
        if (target != null) {
            val from = dto.fromAccountId
                ?: throw RequestException(HttpStatus.BAD_REQUEST, "fromAccountId is required for a goal linked to an account")
            transactions.createTransfer(
                TransferCreateDTO(
                    amount = dto.amount.money(),
                    description = dto.note?.takeIf { it.isNotBlank() } ?: "Sparziel: ${goal.name}",
                    fromAccountId = from.toString(),
                    toAccountId = target.id.toString(),
                    date = Date(),
                ),
                principal,
            )
        } else {
            goal.savedAmount = (goal.savedAmount + dto.amount.money()).money()
        }
        return toDto(repo.save(markAchieved(goal)))
    }

    // -------------------------------------------------------------- helpers

    private fun currentOf(goal: Goal): BigDecimal =
        goal.account?.let { accounts.findById(it.id!!).map { a -> a.currentBalance.orZero() }.orElse(Money.ZERO) } ?: goal.savedAmount

    private fun markAchieved(goal: Goal): Goal {
        val done = currentOf(goal) >= goal.targetAmount
        if (done && goal.achievedAt == null) goal.achievedAt = Date()
        if (!done) goal.achievedAt = null
        return goal
    }

    private fun owned(id: UUID, userId: UUID): Goal {
        val goal = repo.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Goal not found") }
        if (goal.user?.id != userId) throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        return goal
    }

    private fun ownedAccount(id: UUID, userId: UUID): UserAccount =
        accounts.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Account not found") }
            .also { if (it.user?.id != userId) throw RequestException(HttpStatus.FORBIDDEN, "Access denied") }

    private fun toDto(g: Goal): GoalResponseDTO {
        val current = currentOf(g)
        val remaining = (g.targetAmount - current).money().max(Money.ZERO)
        val pct = if (g.targetAmount.signum() > 0) current.divide(g.targetAmount, 6, RoundingMode.HALF_UP).toDouble() * 100 else 100.0
        val today = LocalDate.now()
        val monthsLeft = g.targetDate?.let { ChronoUnit.MONTHS.between(today, it).toInt().coerceAtLeast(0) }
        val perMonth = monthsLeft?.let { m ->
            if (remaining.signum() == 0) Money.ZERO
            else remaining.divide(BigDecimal(maxOf(1, m)), 2, RoundingMode.CEILING)
        }
        val onTrack = g.targetDate?.let { end ->
            val start = g.createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            val total = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)
            val elapsed = ChronoUnit.DAYS.between(start, today).coerceIn(0, total)
            val expected = g.targetAmount.multiply(BigDecimal(elapsed)).divide(BigDecimal(total), 2, RoundingMode.HALF_UP)
            current >= expected
        }
        return GoalResponseDTO(
            id = g.id!!,
            name = g.name,
            targetAmount = g.targetAmount,
            targetDate = g.targetDate,
            accountId = g.account?.id,
            accountName = g.account?.accountName,
            current = current,
            remaining = remaining,
            percentage = pct.coerceAtMost(100.0),
            monthsLeft = monthsLeft,
            requiredPerMonth = perMonth,
            onTrack = onTrack,
            achieved = g.achievedAt != null,
            color = g.color,
            icon = g.icon,
            isActive = g.isActive,
            createdAt = g.createdAt,
        )
    }
}
