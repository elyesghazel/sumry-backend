package ch.sumry.core.module.transaction.service

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.types.CategoryType
import ch.sumry.core.types.Money
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Turns "12.50 kebab cash yesterday" into a booking without a form.
 *
 * Deterministic on purpose: the same sentence always parses the same way, and what it
 * could not work out is reported as missing rather than guessed. Categories come from an
 * explicit `#label`, a label or category word in the text, or — left to the caller — the
 * rules.
 */
object QuickEntryParser {

    data class Parsed(
        val amount: BigDecimal?,
        val description: String,
        val date: LocalDate,
        val account: UserAccount?,
        val toAccount: UserAccount?,
        val category: Category?,
        /** True when the wording says money came in, even before a category is known. */
        val incomeHint: Boolean,
        val isTransfer: Boolean,
        val missing: List<String>,
    )

    private val amountRe = Regex("""(?<![\w.,])([+-]?)(?:(?:chf|fr\.?|sfr|eur|€|usd|\$)\s*)?(\d{1,3}(?:'\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?:\s*(?:chf|fr\.?|sfr|eur|€|usd|\$|\.-|\.–))?(?![\w])""", RegexOption.IGNORE_CASE)
    private val hashtagRe = Regex("""#([\p{L}\p{N}&_\-]+)""")
    private val dateNumRe = Regex("""\b(\d{1,2})\.(\d{1,2})\.?(\d{2,4})?\b""")
    private val isoRe = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")

    private val incomeWords = setOf("got", "received", "income", "earned", "salary", "refund", "erhalten", "bekommen", "einnahme", "lohn", "rückerstattung", "verdient", "eingenommen", "+")
    private val transferWords = setOf("transfer", "moved", "move", "überweisung", "überwiesen", "umbuchung", "verschoben", "abgehoben", "withdrew", "withdraw", "bezug", "bezogen")
    private val toWords = setOf("to", "->", "→", "nach", "auf", "zu", "an")
    private val fromWords = setOf("from", "von", "ab")
    private val stopWords = setOf("for", "on", "at", "in", "the", "a", "an", "für", "bei", "im", "am", "mit", "und", "and", "spent", "paid", "ausgegeben", "bezahlt", "gekauft", "bought", "chf", "fr", "fr.", "sfr", "eur", "usd")
    private val accountAliases = mapOf(
        "cash" to listOf("cash", "bar", "portemonnaie", "wallet", "bargeld"),
        "postfinance" to listOf("postfinance", "pf", "post", "konto"),
        "revolut" to listOf("revolut", "revo", "rev"),
        "sparkonto" to listOf("sparkonto", "savings", "sparen", "spar"),
    )

    fun parse(input: String, accounts: List<UserAccount>, categories: List<Category>, today: LocalDate = LocalDate.now()): Parsed {
        var text = input.trim()
        val missing = ArrayList<String>()

        // --- amount
        var amount: BigDecimal? = null
        var incomeHint = false
        amountRe.find(text)?.let { m ->
            val raw = m.groupValues[2].replace("'", "").replace(',', '.')
            amount = raw.toBigDecimalOrNull()?.let { Money.of(it) }
            if (m.groupValues[1] == "+") incomeHint = true
            text = text.removeRange(m.range).trim()
        }
        if (amount == null || amount!!.signum() <= 0) missing += "amount"

        // --- date
        var date = today
        isoRe.find(text)?.let { m ->
            runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull()?.let { date = it; text = text.removeRange(m.range) }
        }
        dateNumRe.find(text)?.let { m ->
            val day = m.groupValues[1].toInt(); val month = m.groupValues[2].toInt()
            val year = m.groupValues[3].let { if (it.isEmpty()) today.year else if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { d ->
                // A day/month without a year that lies ahead means last year's, not the future.
                date = if (m.groupValues[3].isEmpty() && d.isAfter(today)) d.minusYears(1) else d
                text = text.removeRange(m.range)
            }
        }
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        fun take(vararg options: String): Boolean {
            val i = words.indexOfFirst { it.lowercase().trim(',', '.') in options }
            if (i < 0) return false
            words.removeAt(i); return true
        }
        when {
            take("today", "heute") -> date = today
            take("yesterday", "gestern") -> date = today.minusDays(1)
            take("vorgestern") -> date = today.minusDays(2)
        }
        for ((names, dow) in weekdays) {
            if (take(*names)) { date = today.with(TemporalAdjusters.previous(dow)); break }
        }

        // --- transfer: "<amount> [from] X to Y", with or without the word transfer
        var isTransfer = words.any { it.lowercase() in transferWords }
        words.removeAll { it.lowercase() in transferWords }
        var account: UserAccount? = null
        var toAccount: UserAccount? = null
        val toIdx = words.indexOfFirst { it.lowercase() in toWords }
        if (toIdx >= 0 && toIdx + 1 < words.size) {
            matchAccount(words[toIdx + 1], accounts)?.let { to ->
                toAccount = to
                words.removeAt(toIdx + 1); words.removeAt(toIdx)
                val fromIdx = words.indexOfFirst { it.lowercase() in fromWords }
                val fromWord = if (fromIdx >= 0 && fromIdx + 1 < words.size) words[fromIdx + 1] else words.firstNotNullOfOrNull { w -> matchAccount(w, accounts)?.let { w } }
                fromWord?.let { w -> matchAccount(w, accounts)?.let { account = it; words.remove(w) } }
                if (fromIdx >= 0 && fromIdx < words.size && words[fromIdx].lowercase() in fromWords) words.removeAt(fromIdx)
                isTransfer = true
            }
        }
        if (toAccount == null) {
            // Ordinary entry: any account name anywhere in the sentence.
            val hit = words.firstNotNullOfOrNull { w -> matchAccount(w, accounts)?.let { w to it } }
            if (hit != null) { account = hit.second; words.remove(hit.first) }
        }
        if (isTransfer && (account == null || toAccount == null)) missing += "accounts (transfer needs from and to)"

        // --- category: #label, or a word that is a label
        var category: Category? = null
        hashtagRe.find(words.joinToString(" "))?.let { m ->
            category = categories.firstOrNull { it.label.equals(m.groupValues[1].replace('_', ' '), ignoreCase = true) }
            words.removeAll { it.contains(m.value) }
        }
        if (category == null && !isTransfer) {
            val labelHit = categories.firstOrNull { c ->
                val label = c.label?.lowercase() ?: return@firstOrNull false
                words.any { it.lowercase().trim(',', '.') == label } ||
                    (label.contains(' ') && words.joinToString(" ").lowercase().contains(label))
            }
            if (labelHit != null) {
                category = labelHit
                val label = labelHit.label!!.lowercase()
                words.removeAll { it.lowercase().trim(',', '.') == label }
            }
        }
        if (category != null && category!!.categoryType == CategoryType.INCOME) incomeHint = true

        // --- income wording, after categories so a label like "Lohn" is not eaten as a hint word
        if (words.any { it.lowercase().trim(',', '.') in incomeWords }) incomeHint = true
        words.removeAll { it.lowercase().trim(',', '.') in incomeWords && it != "+" }

        val description = words
            .filter { it.lowercase().trim(',', '.') !in stopWords }
            .filter { it.any { c -> c.isLetterOrDigit() } } // a stray "." left by a date
            .joinToString(" ").trim()
        return Parsed(
            amount = amount,
            description = description.ifEmpty { category?.label ?: (if (isTransfer) "Transfer" else "") },
            date = date,
            account = account ?: accounts.firstOrNull { it.accountName.equals("PostFinance", true) } ?: accounts.firstOrNull(),
            toAccount = toAccount,
            category = category,
            incomeHint = incomeHint,
            isTransfer = isTransfer,
            missing = missing,
        )
    }

    private val weekdays = listOf(
        arrayOf("monday", "montag", "mo") to DayOfWeek.MONDAY,
        arrayOf("tuesday", "dienstag", "di") to DayOfWeek.TUESDAY,
        arrayOf("wednesday", "mittwoch", "mi") to DayOfWeek.WEDNESDAY,
        arrayOf("thursday", "donnerstag", "do") to DayOfWeek.THURSDAY,
        arrayOf("friday", "freitag", "fr") to DayOfWeek.FRIDAY,
        arrayOf("saturday", "samstag", "sa") to DayOfWeek.SATURDAY,
        arrayOf("sunday", "sonntag", "so") to DayOfWeek.SUNDAY,
    )

    private fun matchAccount(word: String, accounts: List<UserAccount>): UserAccount? {
        val w = word.lowercase().trim(',', '.', '(', ')')
        if (w.length < 2) return null
        accounts.firstOrNull { it.accountName?.lowercase() == w }?.let { return it }
        accounts.firstOrNull { a -> a.accountName?.lowercase()?.let { it.startsWith(w) && w.length >= 3 } == true }?.let { return it }
        for ((canonical, aliases) in accountAliases) {
            if (w in aliases) accounts.firstOrNull { it.accountName?.lowercase()?.contains(canonical) == true }?.let { return it }
        }
        return null
    }

    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}
