package ch.sumry.core.module.transaction.service

import ch.sumry.core.privacy.maskAccounts
import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.transaction.dto.CashflowDTO
import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransactionPageDTO
import ch.sumry.core.module.transaction.dto.QuickEntryDTO
import ch.sumry.core.module.transaction.dto.QuickEntryParsed
import ch.sumry.core.module.transaction.dto.QuickEntryRequest
import ch.sumry.core.module.transaction.dto.TransactionSearchQuery
import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.transaction.dto.TransactionUpdateDTO
import ch.sumry.core.module.transaction.dto.TransferCreateDTO
import ch.sumry.core.module.transaction.dto.TransferResponseDTO
import ch.sumry.core.module.transaction.dto.TransferUpdateDTO
import ch.sumry.core.module.transaction.mapper.TransactionMapper
import ch.sumry.core.module.transaction.model.Transaction
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.module.transaction.repo.TransactionSearch
import ch.sumry.core.module.rule.service.RuleService
import ch.sumry.core.module.fx.FxService
import ch.sumry.core.module.user.repo.UserPreferencesRepository
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.types.CategoryType
import ch.sumry.core.types.Money
import ch.sumry.core.types.TransactionKind
import ch.sumry.core.types.money
import ch.sumry.core.types.orZero
import ch.sumry.core.types.sumMoney
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.Principal
import java.time.YearMonth
import java.time.ZoneId
import java.util.*

/** The label adjustments are booked under. */
const val ADJUSTMENT_LABEL = "Einstellung"

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val userAccountRepository: UserAccountRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val search: TransactionSearch,
    private val rules: RuleService,
    private val fx: FxService,
    private val preferences: UserPreferencesRepository,
) {

    // ---------------------------------------------------------------- helpers

    /** Loads an account and fails unless it belongs to [userId]. */
    private fun ownedAccount(accountId: UUID, userId: UUID): UserAccount {
        val account = userAccountRepository.findById(accountId).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "Account not found")
        }
        if (account.user?.id != userId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Forbidden: Account access denied")
        }
        return account
    }

    /** Applies a transaction's effect to the account balance and returns (before, after). */
    private fun applyEffect(account: UserAccount, kind: TransactionKind, amount: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val before = account.currentBalance.orZero()
        val after = (before + kind.signedEffect(amount)).money()
        account.currentBalance = after
        return before to after
    }

    /** Undoes a transaction's effect on the account balance. */
    private fun revertEffect(account: UserAccount, kind: TransactionKind, amount: BigDecimal) {
        account.currentBalance = (account.currentBalance.orZero() - kind.signedEffect(amount)).money()
    }

    /** The currency every report adds up in. */
    fun baseCurrency(userId: UUID): String =
        preferences.findByUser_Id(userId).map { it.preferredCurrency }.orElse("CHF").uppercase()

    /**
     * Stamps a transaction with its account's currency, the rate to the base currency at
     * booking time, and the resulting base amount. Called on every write so history keeps
     * the value it had, whatever the rate does afterwards.
     */
    private fun price(transaction: Transaction, account: UserAccount, userId: UUID) {
        val currency = account.currency.uppercase()
        val rate = fx.rate(currency, baseCurrency(userId))
        transaction.currency = currency
        transaction.fxRate = rate
        transaction.baseAmount = transaction.amount.orZero().multiply(rate).money()
    }

    private fun parseUuid(value: String, field: String): UUID =
        try {
            UUID.fromString(value)
        } catch (ex: IllegalArgumentException) {
            throw RequestException(HttpStatus.BAD_REQUEST, "$field is not a valid id")
        }

    // ------------------------------------------------------------- read paths

    fun getByUserId(userId: UUID, principal: Principal): List<TransactionResponseDTO> {

        val providedUserId = UUID.fromString(principal.name)

        if (providedUserId != userId) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }

        val transactions = transactionRepository.findByUserAccount_User_Id(userId)

        return TransactionMapper.toDtoList(transactions)
    }

    /** Filtered, sorted, paged — the list the Activity screen scrolls, with totals for the whole match. */
    fun search(query: TransactionSearchQuery, principal: Principal): TransactionPageDTO {
        val userId = UUID.fromString(principal.name)
        val page = transactionRepository.findAll(search.specification(userId, query), search.pageable(query))
        return TransactionPageDTO(
            items = TransactionMapper.toDtoList(page.content),
            page = page.number,
            size = page.size,
            total = page.totalElements,
            totalPages = page.totalPages,
            sums = search.sums(userId, query),
        )
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    fun getByUserIdAdmin(userId: UUID, principal: Principal): List<TransactionResponseDTO> {

        val transactions = transactionRepository.findByUserAccount_User_Id(userId)

        return TransactionMapper.toDtoList(transactions)
    }

    // ------------------------------------------------------------ quick entry

    /**
     * "12.50 kebab cash yesterday" → parsed, and booked when asked and complete.
     * A category can come from the text or from the rules; with neither, the entry is
     * returned as not ready and the caller fills the gap.
     */
    @Transactional
    fun quick(req: QuickEntryRequest, principal: Principal): QuickEntryDTO {
        val userId = UUID.fromString(principal.name)
        val accounts = userAccountRepository.findByUserId(userId)
        val categories = categoryRepository.findAll().filter { it.user?.id == userId && it.isActive != false }
        val parsed = QuickEntryParser.parse(req.text, accounts, categories)
        val missing = parsed.missing.toMutableList()

        val account = req.accountId?.let { ownedAccount(it, userId) } ?: parsed.account
        if (account == null) missing += "account"

        var category = parsed.category
        var source: String? = category?.let { "explicit" }
        if (category == null && !parsed.isTransfer && account != null) {
            category = rules.suggest(userId, parsed.description, account.id)
            if (category != null) source = "rule"
        }
        if (category == null && !parsed.isTransfer) missing += "category"
        if (parsed.incomeHint && category?.categoryType == CategoryType.EXPENSE) {
            missing += "category (the text says income but '${category?.label}' is an expense category)"
        }
        val kind = when {
            parsed.isTransfer -> TransactionKind.TRANSFER_OUT
            category?.categoryType == CategoryType.INCOME -> TransactionKind.INCOME
            else -> TransactionKind.EXPENSE
        }
        val ready = missing.isEmpty()
        val date = Date.from(parsed.date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant())

        var booked: TransactionResponseDTO? = null
        var groupId: UUID? = null
        if (req.book && ready) {
            if (parsed.isTransfer) {
                groupId = createTransfer(
                    TransferCreateDTO(parsed.amount!!, parsed.description.takeIf { it.isNotBlank() && it != "Transfer" }, account!!.id.toString(), parsed.toAccount!!.id.toString(), date),
                    principal,
                ).transferGroupId
            } else {
                booked = logTransaction(
                    TransactionCreateDTO(parsed.amount!!, parsed.description.takeIf { it.isNotBlank() }, category!!.label, date, account!!.id.toString()),
                    principal,
                )
            }
        }
        return QuickEntryDTO(
            parsed = QuickEntryParsed(
                amount = parsed.amount,
                description = maskAccounts(parsed.description),
                date = parsed.date,
                kind = kind,
                accountId = account?.id,
                accountName = account?.accountName,
                toAccountId = parsed.toAccount?.id,
                toAccountName = parsed.toAccount?.accountName,
                categoryLabel = category?.label,
                categoryColor = category?.color,
                categorySource = source,
            ),
            missing = missing,
            ready = ready,
            booked = booked,
            transferGroupId = groupId,
        )
    }

    // ----------------------------------------------------- ordinary movements

    @Transactional
    fun logTransaction(dto: TransactionCreateDTO, principal: Principal): TransactionResponseDTO {
        val authenticatedUserId = UUID.fromString(principal.name)

        // 1. Load User Account and check Ownership
        val userAccountEntity = ownedAccount(parseUuid(dto.userAccountId, "userAccountId"), authenticatedUserId)

        // 2. The category: named explicitly, or chosen by the rules from the note.
        val label = dto.categoryLabel?.trim()?.takeIf { it.isNotEmpty() }
        val categoryEntity = if (label != null) {
            categoryRepository.findByLabelAndUser_Id(label, authenticatedUserId).orElseThrow {
                RequestException(HttpStatus.NOT_FOUND, "Category not found")
            }
        } else {
            rules.categorise(authenticatedUserId, dto.description, userAccountEntity.id)
                ?: throw RequestException(HttpStatus.BAD_REQUEST, "No category given and no rule matches '${dto.description ?: ""}'")
        }

        // 3. Map DTO to Entity using the loaded objects
        val transaction = TransactionMapper.fromDtoToEntity(
            dto,
            categoryEntity,
            userAccountEntity
        )

        // 4. Apply the balance effect
        val (before, after) = applyEffect(
            userAccountEntity,
            transaction.effectiveKind(),
            transaction.amount.orZero()
        )
        transaction.balanceBefore = before
        transaction.balanceAfter = after
        price(transaction, userAccountEntity, authenticatedUserId)

        // 5. Speichern
        userAccountRepository.save(userAccountEntity)
        transactionRepository.save(transaction)

        return TransactionMapper.toDto(transaction)
    }

    @Transactional
    fun delete(id: UUID, principal: Principal) {
        val userId = UUID.fromString(principal.name)

        // 1. load
        val transaction = transactionRepository.findById(id).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "Transaction not found")
        }

        // 2. principal ownership
        if (transaction.userAccount?.user?.id != userId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        }

        // A transfer is one unit of work: deleting either leg removes both.
        val groupId = transaction.transferGroupId
        if (groupId != null) {
            deleteTransfer(groupId, principal)
            return
        }

        val account = transaction.userAccount!!
        revertEffect(account, transaction.effectiveKind(), transaction.amount.orZero())

        userAccountRepository.save(account)
        transactionRepository.delete(transaction)
    }

    @Transactional
    fun update(id: UUID, dto: TransactionUpdateDTO, principal: Principal): TransactionResponseDTO {
        val userId = UUID.fromString(principal.name)

        // 1. Load the existing transaction
        val transaction = transactionRepository.findById(id).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "Transaction not found")
        }

        // 2. Security Check (Ownership of the CURRENT transaction)
        if (transaction.userAccount?.user?.id != userId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        }

        if (transaction.transferGroupId != null) {
            throw RequestException(
                HttpStatus.BAD_REQUEST,
                "This is a transfer leg. Use PUT /api/transactions/transfer/{transferGroupId}."
            )
        }

        // 3. Resolve the target account (defaults to the current one)
        val oldAccount = transaction.userAccount!!
        val targetAccount = if (dto.userAccountId != null) {
            ownedAccount(parseUuid(dto.userAccountId, "userAccountId"), userId)
        } else {
            oldAccount
        }

        // 4. Revert the old effect from the old account
        revertEffect(oldAccount, transaction.effectiveKind(), transaction.amount.orZero())
        userAccountRepository.save(oldAccount)

        // 5. Apply the requested changes
        if (dto.description != null) transaction.description = maskAccounts(dto.description)
        if (dto.date != null) transaction.date = dto.date
        if (dto.amount != null) transaction.amount = dto.amount.money()

        if (dto.categoryLabel != null) {
            val newCategory = categoryRepository.findByLabelAndUser_IdAndIsActiveTrue(dto.categoryLabel, userId)
                .orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Category '${dto.categoryLabel}' not found") }
            transaction.category = newCategory
            transaction.kind = newCategory.categoryType?.let { TransactionKind.of(it) } ?: transaction.kind
        }

        transaction.userAccount = targetAccount

        // 6. Apply the new effect to the target account.
        //    When the account did not change, `targetAccount` IS `oldAccount` and already carries
        //    the reverted balance, so this reads the correct base either way.
        val (before, after) = applyEffect(
            targetAccount,
            transaction.effectiveKind(),
            transaction.amount.orZero()
        )
        transaction.balanceBefore = before
        transaction.balanceAfter = after
        price(transaction, targetAccount, userId)

        userAccountRepository.save(targetAccount)
        val savedTransaction = transactionRepository.save(transaction)

        return TransactionMapper.toDto(savedTransaction)
    }

    // -------------------------------------------------------------- transfers

    @Transactional
    fun createTransfer(dto: TransferCreateDTO, principal: Principal): TransferResponseDTO {
        val userId = UUID.fromString(principal.name)

        val fromId = parseUuid(dto.fromAccountId, "fromAccountId")
        val toId = parseUuid(dto.toAccountId, "toAccountId")
        if (fromId == toId) {
            throw RequestException(HttpStatus.BAD_REQUEST, "Source and destination account must differ")
        }

        val from = ownedAccount(fromId, userId)
        val to = ownedAccount(toId, userId)

        val groupId = UUID.randomUUID()
        val amount = dto.amount.money()
        val toAmount = arrivingAmount(amount, from, to, dto.toAmount)
        writeTransferLegs(groupId, from, to, amount, toAmount, dto.description, dto.date, userId)

        return TransferResponseDTO(
            transferGroupId = groupId,
            amount = amount,
            description = dto.description,
            date = dto.date,
            fromAccountId = from.id,
            toAccountId = to.id,
            toAmount = toAmount,
        )
    }

    @Transactional
    fun updateTransfer(groupId: UUID, dto: TransferUpdateDTO, principal: Principal): TransferResponseDTO {
        val userId = UUID.fromString(principal.name)
        val legs = ownedTransferLegs(groupId, userId)

        val outLeg = legs.first { it.effectiveKind() == TransactionKind.TRANSFER_OUT }
        val inLeg = legs.first { it.effectiveKind() == TransactionKind.TRANSFER_IN }

        // Revert both legs before recomputing.
        revertEffect(outLeg.userAccount!!, TransactionKind.TRANSFER_OUT, outLeg.amount.orZero())
        revertEffect(inLeg.userAccount!!, TransactionKind.TRANSFER_IN, inLeg.amount.orZero())
        userAccountRepository.save(outLeg.userAccount!!)
        userAccountRepository.save(inLeg.userAccount!!)

        val amount = dto.amount?.money() ?: outLeg.amount.orZero()
        val description = dto.description ?: outLeg.description
        val date = dto.date ?: outLeg.date ?: Date()

        val fromId = dto.fromAccountId?.let { parseUuid(it, "fromAccountId") } ?: outLeg.userAccount!!.id!!
        val toId = dto.toAccountId?.let { parseUuid(it, "toAccountId") } ?: inLeg.userAccount!!.id!!
        if (fromId == toId) {
            throw RequestException(HttpStatus.BAD_REQUEST, "Source and destination account must differ")
        }

        val from = ownedAccount(fromId, userId)
        val to = ownedAccount(toId, userId)

        // Rewrite the pair from scratch so account changes are handled uniformly.
        transactionRepository.deleteAll(legs)
        transactionRepository.flush()
        // Keep an explicitly recorded arrival amount when nothing about the money changed.
        val keepArrival = dto.amount == null && dto.toAmount == null &&
            from.id == outLeg.userAccount!!.id && to.id == inLeg.userAccount!!.id
        val toAmount = arrivingAmount(amount, from, to, dto.toAmount ?: if (keepArrival) inLeg.amount else null)
        writeTransferLegs(groupId, from, to, amount, toAmount, description, date, userId)

        return TransferResponseDTO(
            transferGroupId = groupId,
            amount = amount,
            description = description,
            date = date,
            fromAccountId = from.id,
            toAccountId = to.id,
            toAmount = toAmount,
        )
    }

    @Transactional
    fun deleteTransfer(groupId: UUID, principal: Principal) {
        val userId = UUID.fromString(principal.name)
        val legs = ownedTransferLegs(groupId, userId)

        legs.forEach { leg ->
            val account = leg.userAccount!!
            revertEffect(account, leg.effectiveKind(), leg.amount.orZero())
            userAccountRepository.save(account)
        }
        transactionRepository.deleteAll(legs)
    }

    /** Loads both legs of a transfer, failing unless every leg belongs to [userId]. */
    private fun ownedTransferLegs(groupId: UUID, userId: UUID): List<Transaction> {
        val legs = transactionRepository.findByTransferGroupId(groupId)
        if (legs.isEmpty()) {
            throw RequestException(HttpStatus.NOT_FOUND, "Transfer not found")
        }
        if (legs.any { it.userAccount?.user?.id != userId }) {
            throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        }
        return legs
    }

    /** What arrives on the destination: the same amount, or a conversion when the currencies differ. */
    private fun arrivingAmount(amount: BigDecimal, from: UserAccount, to: UserAccount, explicit: BigDecimal?): BigDecimal {
        explicit?.let {
            if (it.signum() <= 0) throw RequestException(HttpStatus.BAD_REQUEST, "toAmount must be positive")
            return it.money()
        }
        return if (from.currency.equals(to.currency, ignoreCase = true)) amount else fx.convert(amount, from.currency, to.currency)
    }

    private fun writeTransferLegs(
        groupId: UUID,
        from: UserAccount,
        to: UserAccount,
        amount: BigDecimal,
        toAmount: BigDecimal,
        description: String?,
        date: Date,
        userId: UUID,
    ) {
        val outLeg = Transaction(
            amount = amount,
            description = description,
            category = null,
            kind = TransactionKind.TRANSFER_OUT,
            transferGroupId = groupId,
            date = date,
            userAccount = from,
        )
        val (outBefore, outAfter) = applyEffect(from, TransactionKind.TRANSFER_OUT, amount)
        outLeg.balanceBefore = outBefore
        outLeg.balanceAfter = outAfter
        price(outLeg, from, userId)

        val inLeg = Transaction(
            amount = toAmount,
            description = description,
            category = null,
            kind = TransactionKind.TRANSFER_IN,
            transferGroupId = groupId,
            date = date,
            userAccount = to,
        )
        val (inBefore, inAfter) = applyEffect(to, TransactionKind.TRANSFER_IN, toAmount)
        inLeg.balanceBefore = inBefore
        inLeg.balanceAfter = inAfter
        price(inLeg, to, userId)

        userAccountRepository.save(from)
        userAccountRepository.save(to)
        transactionRepository.save(outLeg)
        transactionRepository.save(inLeg)
    }

    // ----------------------------------------------------------- reconciliation

    /**
     * Books the difference between an account's recorded balance and its real one.
     *
     * Overwriting the balance directly would leave history that no longer adds up to it,
     * so the gap is written as an ordinary transaction instead. Returns null when the
     * account already matches and there is nothing to book.
     */
    @Transactional
    fun reconcile(accountId: UUID, newBalance: BigDecimal, note: String?, principal: Principal): TransactionResponseDTO? {
        val userId = UUID.fromString(principal.name)
        val account = ownedAccount(accountId, userId)

        val current = account.currentBalance.orZero()
        val delta = (newBalance.money() - current).money()

        // A check that already matched still counts as reconciled.
        account.lastReconciledAt = Date()
        if (Money.isZero(delta)) {
            userAccountRepository.save(account)
            return null
        }

        val category = adjustmentCategory(userId)
        val kind = if (delta.signum() > 0) TransactionKind.INCOME else TransactionKind.EXPENSE

        val transaction = Transaction(
            amount = delta.abs(),
            description = note?.takeIf { it.isNotBlank() } ?: "Abgleich",
            category = category,
            kind = kind,
            date = Date(),
            userAccount = account,
        )

        val (before, after) = applyEffect(account, kind, transaction.amount.orZero())
        transaction.balanceBefore = before
        transaction.balanceAfter = after
        price(transaction, account, userId)
        userAccountRepository.save(account)
        transactionRepository.save(transaction)

        return TransactionMapper.toDto(transaction)
    }

    /**
     * The category adjustments are booked against, created on first use.
     *
     * One category serves both directions: the transaction's own kind carries the sign,
     * so an adjustment up and an adjustment down still group together in reports.
     */
    private fun adjustmentCategory(userId: UUID): Category =
        categoryRepository.findByLabelAndUser_Id(ADJUSTMENT_LABEL, userId).orElseGet {
            categoryRepository.save(
                Category(
                    label = ADJUSTMENT_LABEL,
                    parentGroup = null,
                    color = "#838300",
                    icon = "options",
                    categoryType = CategoryType.EXPENSE,
                    user = userRepository.getReferenceById(userId),
                    isActive = true,
                )
            )
        }

    // ------------------------------------------------------------- reporting

    fun getIncomeAndExpenseMonth(userAccountIdStr: String, principal: Principal): CashflowDTO {
        val userId = UUID.fromString(principal.name)
        val accountId = parseUuid(userAccountIdStr, "userAccountId")

        ownedAccount(accountId, userId)

        val now = YearMonth.now()
        val startOfMonth = Date.from(now.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
        val endOfMonth = Date.from(now.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant())

        val monthlyTransactions = transactionRepository.findByUserAccount_IdAndDateBetween(
            accountId,
            startOfMonth,
            endOfMonth
        )

        // Transfers move money between the user's own accounts — they are neither income nor expense.
        val income = monthlyTransactions
            .filter { it.effectiveKind() == TransactionKind.INCOME }
            .map { it.baseAmount }
            .sumMoney()

        val expenses = monthlyTransactions
            .filter { it.effectiveKind() == TransactionKind.EXPENSE }
            .map { it.baseAmount.abs() }
            .sumMoney()

        return CashflowDTO(
            income = income,
            expenses = expenses
        )
    }


}
