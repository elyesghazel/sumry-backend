package ch.sumry.core.module.porting

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.rule.service.RuleService
import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.transaction.model.Transaction
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.module.transaction.service.TransactionService
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.types.TransactionKind
import ch.sumry.core.types.orZero
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.security.Principal
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@Service
class PortingService(
    private val accounts: UserAccountRepository,
    private val transactions: TransactionRepository,
    private val transactionService: TransactionService,
    private val categories: CategoryRepository,
    private val rules: RuleService,
    private val checker: ImportChecker,
) {
    private val zone: ZoneId = ZoneId.systemDefault()

    // ----------------------------------------------------------------- import

    /**
     * Parses a statement and lines it up against what is already booked. Nothing is
     * written; the caller chooses which rows to commit.
     */
    fun preview(req: ImportPreviewRequest, principal: Principal): ImportPreviewDTO {
        val userId = UUID.fromString(principal.name)
        val account = ownedAccount(req.accountId, userId)
        val parsed = StatementReader.read(req)
        // A PDF or camt file that yields nothing still answers 200: its warnings carry the
        // redacted diagnostics the user needs to get the layout supported.
        val statementFile = parsed.format == PdfStatementParser.FORMAT || parsed.format.startsWith("camt")
        if (parsed.rows.isEmpty() && !statementFile) {
            throw RequestException(HttpStatus.BAD_REQUEST, "No transactions found (${parsed.format}). " + parsed.warnings.joinToString("; "))
        }

        // Existing entries on this account, lined up by amount and nearby day, not by text.
        val entries = transactions.findByUserAccount_User_Id(userId)
            .filter { it.userAccount?.id == account.id && it.date != null && it.id != null }
            .map {
                ImportMatcher.Entry(
                    id = it.id!!,
                    date = it.date!!.toInstant().atZone(zone).toLocalDate(),
                    amount = it.amount.orZero(),
                    kind = it.effectiveKind(),
                    label = it.description?.takeIf { d -> d.isNotBlank() } ?: it.category?.label ?: "entry",
                )
            }
        val matches = ImportMatcher.match(parsed.rows, entries)
        val ruleSet = rules.list(principal)

        val rows = parsed.rows.mapIndexed { i, r ->
            val m = matches[i]
            val suggestion = ruleSet.firstOrNull { rule ->
                rule.isActive && (rule.accountId == null || rule.accountId == account.id) &&
                    rule.categoryType == (if (r.kind == TransactionKind.INCOME) "INCOME" else "EXPENSE") &&
                    rule.matchType.matches(rule.pattern, r.description)
            }?.categoryLabel
            ImportRowDTO(
                line = r.line,
                date = r.date,
                description = r.description,
                rawDescription = r.rawDescription,
                amount = r.amount,
                kind = r.kind,
                currency = r.currency,
                categoryLabel = suggestion,
                duplicate = m != null,
                duplicateOfId = m?.ids?.firstOrNull(),
                currencyMismatch = r.currency != null && !r.currency.equals(account.currency, ignoreCase = true),
                matchIds = m?.ids ?: emptyList(),
                matchKind = m?.kind?.name?.lowercase()?.replace('_', '-'),
                matchNote = m?.note,
                bookingDate = r.bookingDate,
            )
        }
        val closing = parsed.closingBalance ?: parsed.rows.maxByOrNull { it.date }?.balanceAfter
        val computed = ImportMatcher.computedClosing(parsed.openingBalance, parsed.rows)
        return ImportPreviewDTO(
            format = parsed.format,
            accountId = account.id!!,
            accountCurrency = account.currency,
            rows = rows,
            newCount = rows.count { !it.duplicate },
            duplicateCount = rows.count { it.duplicate },
            uncategorisedCount = rows.count { !it.duplicate && it.categoryLabel == null },
            warnings = parsed.warnings,
            statementClosingBalance = closing,
            statementFrom = parsed.statementFrom,
            statementTo = parsed.statementTo,
            statementOpeningBalance = parsed.openingBalance,
            computedClosingBalance = computed,
            balanceOk = if (computed != null && closing != null) computed.compareTo(closing) == 0 else null,
            aiCheckAvailable = checker.enabledFor(userId),
        )
    }

    /** Books the chosen rows one by one; a row that cannot be booked is reported, not fatal. */
    fun commit(req: ImportCommitRequest, principal: Principal): ImportCommitDTO {
        val userId = UUID.fromString(principal.name)
        val account = ownedAccount(req.accountId, userId)
        val byLabel = categories.findAll().filter { it.user?.id == userId && it.isActive != false }.associateBy { it.label }
        val booked = ArrayList<TransactionResponseDTO>()
        val skipped = ArrayList<ImportSkipped>()
        for (row in req.rows.sortedBy { it.date }) {
            val label = row.categoryLabel?.takeIf { it.isNotBlank() }
                ?: if (row.kind == TransactionKind.INCOME) req.defaultIncomeCategory else req.defaultExpenseCategory
            if (label.isNullOrBlank()) { skipped += ImportSkipped(row.line, "no category"); continue }
            val category = byLabel[label] ?: run { skipped += ImportSkipped(row.line, "unknown category '$label'"); null } ?: continue
            val wanted = if (row.kind == TransactionKind.INCOME) "INCOME" else "EXPENSE"
            if (category.categoryType?.name != wanted) {
                skipped += ImportSkipped(row.line, "'$label' is an ${category.categoryType} category but the row is $wanted"); continue
            }
            if (row.amount.signum() <= 0) { skipped += ImportSkipped(row.line, "amount must be positive"); continue }
            try {
                booked += transactionService.logTransaction(
                    TransactionCreateDTO(
                        amount = row.amount,
                        description = row.description?.trim()?.takeIf { it.isNotEmpty() },
                        categoryLabel = label,
                        date = Date.from(row.date.atTime(12, 0).atZone(zone).toInstant()),
                        userAccountId = account.id.toString(),
                    ),
                    principal,
                )
            } catch (ex: RequestException) {
                skipped += ImportSkipped(row.line, ex.message)
            }
        }
        return ImportCommitDTO(booked.size, skipped, booked)
    }

    // ----------------------------------------------------------------- export

    fun exportRows(principal: Principal, from: LocalDate?, to: LocalDate?, accountId: UUID?): List<Transaction> {
        val userId = UUID.fromString(principal.name)
        val fromDate = from?.let { Date.from(it.atStartOfDay(zone).toInstant()) }
        val toDate = to?.let { Date.from(it.plusDays(1).atStartOfDay(zone).toInstant()) }
        return transactions.findByUserAccount_User_Id(userId)
            .asSequence()
            .filter { accountId == null || it.userAccount?.id == accountId }
            .filter { fromDate == null || (it.date != null && !it.date!!.before(fromDate)) }
            .filter { toDate == null || (it.date != null && it.date!!.before(toDate)) }
            .sortedBy { it.date }
            .toList()
    }

    fun toCsv(rows: List<Transaction>): String {
        val sb = StringBuilder("date,account,kind,category,description,amount,currency,base_amount,balance_after,transfer_group,id\n")
        val fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
        for (t in rows) {
            val date = t.date?.toInstant()?.atZone(zone)?.toLocalDateTime()?.format(fmt) ?: ""
            sb.append(listOf(
                date,
                t.userAccount?.accountName ?: "",
                t.effectiveKind().name,
                t.category?.label ?: "",
                t.description ?: "",
                t.amount.orZero().toPlainString(),
                t.currency,
                t.baseAmount.toPlainString(),
                t.balanceAfter?.toPlainString() ?: "",
                t.transferGroupId?.toString() ?: "",
                t.id.toString(),
            ).joinToString(",") { csvCell(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun csvCell(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"${v.replace("\"", "\"\"")}\"" else v

    private fun ownedAccount(id: UUID, userId: UUID): UserAccount =
        accounts.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Account not found") }
            .also { if (it.user?.id != userId) throw RequestException(HttpStatus.FORBIDDEN, "Access denied") }
}
