package ch.sumry.core.module.shortcut

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.rule.service.RuleService
import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.model.Transaction
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.module.transaction.service.TransactionService
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.types.CategoryType
import ch.sumry.core.types.TransactionKind
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.Principal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * The two calls an Apple Pay automation needs: which category, then book it. Both lean on
 * history so the shortcut needs no configuration beyond its token.
 */
@Service
class ShortcutService(
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val accounts: UserAccountRepository,
    private val rules: RuleService,
    private val transactionService: TransactionService,
) {

    fun categories(principal: Principal, merchant: String?): ShortcutCategoriesDTO {
        val userId = UUID.fromString(principal.name)
        val labels = categories.findByUser_Id(userId)
            .filter { it.isActive != false && it.categoryType == CategoryType.EXPENSE }
            .mapNotNull { it.label }
        val expenses = recentExpenses(userId, days = 180)
        val usage = expenses.groupingBy { it.category?.label }.eachCount()
        val suggested = (rules.suggest(userId, merchant, null)?.label ?: fromHistory(expenses, merchant))
            ?.takeIf { it in labels }
        val ordered = labels.sortedWith(compareByDescending<String> { usage[it] ?: 0 }.thenBy { it.lowercase() })
        return ShortcutCategoriesDTO(suggested, listOfNotNull(suggested) + ordered.filter { it != suggested })
    }

    fun log(principal: Principal, dto: ShortcutLogDTO): ShortcutLogResultDTO {
        val userId = UUID.fromString(principal.name)
        val amount = parseAmount(dto.amount)
            ?: throw RequestException(HttpStatus.BAD_REQUEST, "No amount in '${dto.amount ?: ""}' - run it from a real payment")
        val category = dto.category?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw RequestException(HttpStatus.BAD_REQUEST, "No category chosen")
        val owned = accounts.findByUserId(userId)
        if (owned.isEmpty()) throw RequestException(HttpStatus.CONFLICT, "No account to book on")
        val account = matchAccount(owned, dto.account) ?: matchAccount(owned, dto.card)
            ?: mostUsedAccount(userId, owned)
        val merchant = dto.merchant?.trim()?.takeIf { it.isNotEmpty() }
        val booked = transactionService.logTransaction(
            TransactionCreateDTO(
                amount = amount,
                description = merchant,
                categoryLabel = category,
                date = Date(),
                userAccountId = account.id.toString(),
            ),
            principal,
        )
        val text = "${amount.setScale(2, RoundingMode.HALF_UP).toPlainString()} ${account.currency}" +
            (merchant?.let { " · $it" } ?: "") + " → $category (${account.accountName})"
        return ShortcutLogResultDTO(booked.id, text)
    }

    // ------------------------------------------------------------------ helpers

    private fun recentExpenses(userId: UUID, days: Long): List<Transaction> {
        val since = Date.from(Instant.now().minus(days, ChronoUnit.DAYS))
        return transactions.findByUserAccount_User_Id(userId)
            .filter { it.kind == TransactionKind.EXPENSE && it.date?.after(since) == true }
    }

    /** The category most often used before for a note that names the same merchant. */
    private fun fromHistory(expenses: List<Transaction>, merchant: String?): String? {
        val key = merchantKey(merchant) ?: return null
        return expenses
            .filter { it.description?.lowercase()?.contains(key) == true }
            .groupingBy { it.category?.label }
            .eachCount()
            .filterKeys { it != null }
            .maxByOrNull { it.value }
            ?.key
    }

    private fun mostUsedAccount(userId: UUID, owned: List<UserAccount>): UserAccount {
        val counts = recentExpenses(userId, days = 90).groupingBy { it.userAccount?.id }.eachCount()
        return owned.maxByOrNull { counts[it.id] ?: 0 }!!
    }

    companion object {
        /** "CHF 12.50", "12,50", "1'234.50", "-12.50" → 12.50. */
        fun parseAmount(raw: String?): BigDecimal? {
            var s = raw?.replace(Regex("[^0-9.,]"), "") ?: return null
            if (s.isEmpty()) return null
            s = when {
                s.contains('.') && s.contains(',') ->
                    if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
                s.contains(',') -> s.replace(',', '.')
                else -> s
            }
            return s.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        }

        /** First word of the merchant that carries meaning: "MIGROS M ZUERICH" → "migros". */
        fun merchantKey(text: String?): String? =
            text?.lowercase()?.split(Regex("[^\\p{L}\\p{N}]+"))?.firstOrNull { it.length >= 3 && !it.all(Char::isDigit) }

        /** An account whose name and the given text share a word, e.g. card "PostFinance Card". */
        fun matchAccount(owned: List<UserAccount>, text: String?): UserAccount? {
            val words = text?.lowercase()?.split(Regex("[^\\p{L}\\p{N}]+"))?.filter { it.length >= 3 }?.toSet()
            if (words.isNullOrEmpty()) return null
            return owned.firstOrNull { a ->
                val name = a.accountName?.lowercase() ?: return@firstOrNull false
                name.split(Regex("[^\\p{L}\\p{N}]+")).any { it.length >= 3 && it in words } ||
                    words.any { name.startsWith(it) || it.startsWith(name) }
            }
        }
    }
}
