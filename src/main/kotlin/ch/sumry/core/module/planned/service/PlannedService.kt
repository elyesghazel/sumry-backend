package ch.sumry.core.module.planned.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.planned.dto.*
import ch.sumry.core.module.planned.repo.PlannedRepository
import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransferCreateDTO
import ch.sumry.core.module.transaction.model.Frequency
import ch.sumry.core.module.transaction.model.PlannedTransaction
import ch.sumry.core.module.transaction.service.TransactionService
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.types.Money
import ch.sumry.core.types.TransactionKind
import ch.sumry.core.types.money
import ch.sumry.core.types.orZero
import ch.sumry.core.types.sumMoney
import org.slf4j.LoggerFactory
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
class PlannedService(
    private val repo: PlannedRepository,
    private val accounts: UserAccountRepository,
    private val categories: CategoryRepository,
    private val users: UserRepository,
    private val transactions: TransactionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ------------------------------------------------------------------ CRUD

    fun list(principal: Principal, includeInactive: Boolean): List<PlannedResponseDTO> {
        val userId = UUID.fromString(principal.name)
        val rows = if (includeInactive) repo.findByUser_IdOrderByStartDateAsc(userId)
        else repo.findByUser_IdAndIsActiveTrueOrderByStartDateAsc(userId)
        val today = LocalDate.now()
        return rows.map { toDto(it, today) }
    }

    @Transactional
    fun create(dto: PlannedCreateDTO, principal: Principal): PlannedResponseDTO {
        val userId = UUID.fromString(principal.name)
        val account = ownedAccount(dto.accountId, userId)
        val to = dto.toAccountId?.let { ownedAccount(it, userId) }
        if (to != null && to.id == account.id) {
            throw RequestException(HttpStatus.BAD_REQUEST, "Source and destination account must differ")
        }
        val category = if (to == null) {
            val label = dto.categoryLabel?.takeIf { it.isNotBlank() }
                ?: throw RequestException(HttpStatus.BAD_REQUEST, "categoryLabel is required unless toAccountId is set")
            categories.findByLabelAndUser_IdAndIsActiveTrue(label, userId)
                .orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Category '$label' not found") }
        } else null
        if (dto.endDate != null && dto.endDate.isBefore(dto.startDate)) {
            throw RequestException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate")
        }
        val planned = PlannedTransaction(
            name = dto.name.trim(),
            amount = dto.amount.money(),
            description = dto.description?.trim()?.takeIf { it.isNotEmpty() },
            startDate = dto.startDate,
            endDate = dto.endDate,
            frequency = dto.frequency,
            category = category,
            userAccount = account,
            toAccount = to,
            user = users.getReferenceById(userId),
            autoBook = dto.autoBook,
        )
        // A plan that starts in the past describes something already happening; the
        // occurrences before today are in the history, not waiting to be booked.
        val today = LocalDate.now()
        planned.lastBookedOn = planned.occurrencesBetween(dto.startDate.minusDays(1), today).lastOrNull()
        return toDto(repo.save(planned), today)
    }

    @Transactional
    fun update(id: UUID, dto: PlannedUpdateDTO, principal: Principal): PlannedResponseDTO {
        val userId = UUID.fromString(principal.name)
        val planned = owned(id, userId)
        dto.name?.let { planned.name = it.trim() }
        dto.amount?.let {
            if (it.signum() <= 0) throw RequestException(HttpStatus.BAD_REQUEST, "amount must be positive")
            planned.amount = it.money()
        }
        dto.description?.let { planned.description = it.trim().takeIf { d -> d.isNotEmpty() } }
        dto.startDate?.let { planned.startDate = it }
        if (dto.clearEndDate) planned.endDate = null else dto.endDate?.let { planned.endDate = it }
        dto.frequency?.let { planned.frequency = it }
        dto.accountId?.let { planned.userAccount = ownedAccount(it, userId) }
        dto.toAccountId?.let { planned.toAccount = ownedAccount(it, userId); planned.category = null }
        dto.categoryLabel?.let { label ->
            planned.category = categories.findByLabelAndUser_IdAndIsActiveTrue(label, userId)
                .orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Category '$label' not found") }
            planned.toAccount = null
        }
        dto.autoBook?.let { planned.autoBook = it }
        dto.isActive?.let { planned.isActive = it }
        if (planned.toAccount != null && planned.toAccount?.id == planned.userAccount?.id) {
            throw RequestException(HttpStatus.BAD_REQUEST, "Source and destination account must differ")
        }
        if (planned.toAccount == null && planned.category == null) {
            throw RequestException(HttpStatus.BAD_REQUEST, "A plan needs a category or a destination account")
        }
        return toDto(repo.save(planned), LocalDate.now())
    }

    @Transactional
    fun delete(id: UUID, principal: Principal) {
        repo.delete(owned(id, UUID.fromString(principal.name)))
    }

    // ------------------------------------------------------------- forecasts

    /** Dated occurrences of every active plan within the next [days] days, plus anything overdue. */
    fun upcoming(principal: Principal, days: Int, accountId: UUID?): List<OccurrenceDTO> {
        val userId = UUID.fromString(principal.name)
        val today = LocalDate.now()
        return occurrences(userId, today, today.plusDays(days.toLong()), accountId)
    }

    /**
     * What is left after everything already committed to before the next salary.
     *
     * balance − planned expenses (and, for a single account, transfers out) falling due
     * before the next planned income. With no income planned, the horizon is 30 days.
     */
    fun safeToSpend(principal: Principal, accountId: UUID?): SafeToSpendDTO {
        val userId = UUID.fromString(principal.name)
        val today = LocalDate.now()
        val owned = accounts.findByUserId(userId)
        val scoped = accountId?.let { id -> owned.firstOrNull { it.id == id } ?: throw RequestException(HttpStatus.NOT_FOUND, "Account not found") }
        val balance = (scoped?.let { listOf(it) } ?: owned).map { it.currentBalance }.sumMoney()

        val plans = repo.findByUser_IdAndIsActiveTrueOrderByStartDateAsc(userId)
        val nextIncome = plans
            .filter { it.kind() == TransactionKind.INCOME && (scoped == null || it.userAccount?.id == scoped.id) }
            .mapNotNull { it.nextOccurrence(today) }
            .minOrNull()
        val horizon = nextIncome ?: today.plusDays(30)
        val reason = if (nextIncome != null) "next planned income" else "no income planned; 30 days"

        val upcoming = occurrences(userId, today, horizon, accountId, plans)
        val expenses = upcoming.filter { it.kind == TransactionKind.EXPENSE }.map { it.amount }.sumMoney()
        // Between own accounts a transfer is neutral; scoped to one account it is money leaving.
        val transfersOut = if (scoped != null) upcoming.filter { it.kind == TransactionKind.TRANSFER_OUT }.map { it.amount }.sumMoney() else Money.ZERO
        val safe = (balance - expenses - transfersOut).money()
        val days = maxOf(1, ChronoUnit.DAYS.between(today, horizon).toInt())
        return SafeToSpendDTO(
            balance = balance,
            accountId = scoped?.id,
            today = today,
            horizon = horizon,
            daysToHorizon = days,
            horizonReason = reason,
            upcomingExpenses = expenses,
            upcomingTransfersOut = transfersOut,
            safeToSpend = safe,
            perDay = safe.divide(BigDecimal(days), 2, RoundingMode.DOWN),
            upcoming = upcoming,
        )
    }

    private fun occurrences(
        userId: UUID,
        today: LocalDate,
        until: LocalDate,
        accountId: UUID?,
        plans: List<PlannedTransaction> = repo.findByUser_IdAndIsActiveTrueOrderByStartDateAsc(userId),
    ): List<OccurrenceDTO> {
        val out = ArrayList<OccurrenceDTO>()
        for (p in plans) {
            if (accountId != null && p.userAccount?.id != accountId && p.toAccount?.id != accountId) continue
            // Anything due since the last booking that was never booked is overdue and still
            // counts — for 90 days, after which an ignored plan stops shouting.
            val after = p.lastBookedOn ?: p.startDate?.minusDays(1) ?: continue
            val from = maxOf(after, today.minusDays(OVERDUE_LOOKBACK_DAYS))
            for (d in p.occurrencesBetween(from, until)) {
                out += OccurrenceDTO(
                    plannedId = p.id!!,
                    name = p.name ?: "",
                    amount = p.amount.orZero(),
                    kind = p.kind(),
                    date = d,
                    categoryLabel = p.category?.label,
                    categoryColor = p.category?.color,
                    accountId = p.userAccount!!.id!!,
                    accountName = p.userAccount!!.accountName ?: "",
                    toAccountName = p.toAccount?.accountName,
                    autoBook = p.autoBook,
                    overdue = d.isBefore(today),
                )
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.name }))
    }

    // --------------------------------------------------------------- booking

    /** Books the next unbooked occurrence on or before today (or the very next one if none is due). */
    @Transactional
    fun bookNext(id: UUID, principal: Principal, onDate: LocalDate?): PlannedResponseDTO {
        val userId = UUID.fromString(principal.name)
        val planned = owned(id, userId)
        val today = LocalDate.now()
        val after = planned.lastBookedOn ?: planned.startDate?.minusDays(1) ?: today.minusDays(1)
        val due = planned.occurrencesBetween(after, today).firstOrNull()
            ?: planned.nextOccurrence(today)
            ?: throw RequestException(HttpStatus.CONFLICT, "This plan has no occurrences left")
        // Paying early books today, not on the future date the plan says.
        book(planned, onDate ?: minOf(due, today), principal)
        planned.lastBookedOn = maxOf(planned.lastBookedOn ?: due, due)
        return toDto(repo.save(planned), today)
    }

    /** Called by the scheduler: books every due occurrence of every auto-booking plan. */
    @Transactional
    fun bookDueAutomatically(today: LocalDate = LocalDate.now()): Int {
        var booked = 0
        for (p in repo.findByIsActiveTrueAndAutoBookTrue()) {
            val principal = Principal { p.user!!.id.toString() }
            // Never book anything that predates the plan itself or the last booking.
            val created = p.createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            val after = maxOf(p.lastBookedOn ?: created.minusDays(1), created.minusDays(1))
            for (d in p.occurrencesBetween(after, today, limit = 12)) {
                try {
                    book(p, d, principal)
                    p.lastBookedOn = d
                    repo.save(p)
                    booked++
                } catch (ex: Exception) {
                    log.error("Auto-booking '{}' for {} failed: {}", p.name, d, ex.message)
                    break
                }
            }
        }
        return booked
    }

    private fun book(p: PlannedTransaction, on: LocalDate, principal: Principal) {
        val date = Date.from(on.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant())
        val note = p.description ?: p.name
        if (p.isTransfer) {
            transactions.createTransfer(
                TransferCreateDTO(
                    amount = p.amount.orZero(),
                    description = note,
                    fromAccountId = p.userAccount!!.id.toString(),
                    toAccountId = p.toAccount!!.id.toString(),
                    date = date,
                ),
                principal,
            )
        } else {
            transactions.logTransaction(
                TransactionCreateDTO(
                    amount = p.amount.orZero(),
                    description = note,
                    categoryLabel = p.category!!.label!!,
                    date = date,
                    userAccountId = p.userAccount!!.id.toString(),
                ),
                principal,
            )
        }
    }

    // --------------------------------------------------------------- helpers

    private companion object {
        const val OVERDUE_LOOKBACK_DAYS = 90L
    }

    private fun owned(id: UUID, userId: UUID): PlannedTransaction {
        val planned = repo.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Plan not found") }
        if (planned.user?.id != userId) throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        return planned
    }

    private fun ownedAccount(id: UUID, userId: UUID): UserAccount {
        val account = accounts.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Account not found") }
        if (account.user?.id != userId) throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        return account
    }

    private fun toDto(p: PlannedTransaction, today: LocalDate) = PlannedResponseDTO(
        id = p.id!!,
        name = p.name ?: "",
        amount = p.amount.orZero(),
        description = p.description,
        startDate = p.startDate!!,
        endDate = p.endDate,
        frequency = p.frequency ?: Frequency.MONTHLY,
        kind = p.kind(),
        categoryLabel = p.category?.label,
        categoryColor = p.category?.color,
        accountId = p.userAccount!!.id!!,
        accountName = p.userAccount!!.accountName ?: "",
        toAccountId = p.toAccount?.id,
        toAccountName = p.toAccount?.accountName,
        autoBook = p.autoBook,
        isActive = p.isActive,
        lastBookedOn = p.lastBookedOn,
        // The next date is the first one neither in the past nor already booked.
        nextDate = p.nextOccurrence(maxOf(today.minusDays(1), p.lastBookedOn ?: today.minusDays(1))),
        monthlyEquivalent = BigDecimal.valueOf((p.frequency ?: Frequency.MONTHLY).perMonth)
            .multiply(p.amount.orZero()).money(),
    )
}
