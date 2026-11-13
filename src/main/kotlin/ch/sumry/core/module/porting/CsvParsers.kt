package ch.sumry.core.module.porting

import ch.sumry.core.types.Money
import ch.sumry.core.types.TransactionKind
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One statement line, already reduced to what Sumry needs. */
data class ParsedRow(
    val line: Int,
    val date: LocalDate,
    val description: String,
    val rawDescription: String,
    /** Always positive; [kind] carries the direction. */
    val amount: BigDecimal,
    val kind: TransactionKind,
    val currency: String?,
    /** Bank-side balance after the row when the statement has one. */
    val balanceAfter: BigDecimal?,
    /** The day the bank booked it, when [date] is the earlier purchase day from the text. */
    val bookingDate: LocalDate? = null,
)

data class ParseResult(
    val format: String,
    val rows: List<ParsedRow>,
    val warnings: List<String>,
    /** Statement period and balances, when the file states them (PDF, camt). */
    val statementFrom: LocalDate? = null,
    val statementTo: LocalDate? = null,
    val openingBalance: BigDecimal? = null,
    val closingBalance: BigDecimal? = null,
)

/**
 * Bank exports differ in delimiter, header language, date shape and how the sign is
 * expressed; the parsers here normalise all of that. Detection looks at the header row,
 * so a file is never misread just because its extension or origin was guessed wrong.
 */
object CsvParsers {

    fun parse(text: String, format: String?): ParseResult {
        val lines = text.replace("﻿", "").lines()
        val chosen = when (format?.lowercase()) {
            null, "", "auto" -> detect(lines)
            else -> format.lowercase()
        }
        return when (chosen) {
            "postfinance" -> postFinance(lines)
            "revolut" -> revolut(lines)
            else -> generic(lines)
        }
    }

    fun detect(lines: List<String>): String {
        val head = lines.take(40).joinToString("\n").lowercase()
        if (head.contains("avisierungstext") || head.contains("gutschrift in") || head.contains("lastschrift in") ||
            head.contains("booking text") || head.contains("credit in chf")) return "postfinance"
        if (head.contains("completed date") && head.contains("started date")) return "revolut"
        return "generic"
    }

    // ------------------------------------------------------------ PostFinance

    private val pfDates = listOf("yyyy-MM-dd", "dd.MM.yyyy", "dd.MM.yy")

    private fun postFinance(lines: List<String>): ParseResult {
        val warnings = ArrayList<String>()
        // The real header is the line that names the amount columns; the preamble also
        // starts with "Datum" ("Datum von:;…") and must not be mistaken for it.
        val headerIdx = lines.indexOfFirst {
            val l = it.lowercase()
            l.count { c -> c == ';' } >= 3 && (l.contains("gutschrift") || l.contains("lastschrift") || l.contains("credit") || l.contains("debit"))
        }
        if (headerIdx < 0) return ParseResult("postfinance", emptyList(), listOf("No PostFinance header row found"))
        val header = split(lines[headerIdx], ';').map { it.lowercase().trim() }
        val iDate = header.indexOfFirst { it.startsWith("datum") || it.startsWith("buchungsdatum") || it == "date" }
        val iText = header.indexOfFirst { it.contains("avisierungstext") || it.contains("booking text") || it.contains("text") }
        val iCredit = header.indexOfFirst { it.startsWith("gutschrift") || it.startsWith("credit") }
        val iDebit = header.indexOfFirst { it.startsWith("lastschrift") || it.startsWith("debit") }
        val iBalance = header.indexOfFirst { it.startsWith("saldo") || it.startsWith("balance") }
        val currency = header.getOrNull(iCredit)?.let { Regex("in ([a-z]{3})").find(it)?.groupValues?.get(1)?.uppercase() }

        val rows = ArrayList<ParsedRow>()
        for ((offset, raw) in lines.drop(headerIdx + 1).withIndex()) {
            val lineNo = headerIdx + 2 + offset
            if (raw.isBlank()) continue
            val cells = split(raw, ';')
            if (cells.size <= maxOf(iDate, iText)) continue
            val date = parseDate(cells[iDate], pfDates) ?: continue // footer/disclaimer lines
            val credit = cells.getOrNull(iCredit)?.let { number(it) }
            val debit = cells.getOrNull(iDebit)?.let { number(it) }
            val (amount, kind) = when {
                credit != null && credit.signum() != 0 -> credit.abs() to TransactionKind.INCOME
                debit != null && debit.signum() != 0 -> debit.abs() to TransactionKind.EXPENSE
                else -> { warnings += "Line $lineNo: no amount"; continue }
            }
            val rawText = cells[iText].trim()
            rows += ParsedRow(
                line = lineNo,
                date = date,
                description = cleanPostFinance(rawText),
                rawDescription = rawText,
                amount = Money.of(amount),
                kind = kind,
                currency = currency,
                balanceAfter = cells.getOrNull(iBalance)?.let { number(it) }?.let { Money.of(it) },
            )
        }
        return ParseResult("postfinance", rows, warnings)
    }

    private val pfNoise = listOf(
        // Card and wallet payments: "APPLE PAY KAUF/DIENSTLEISTUNG VOM 01.10.2026", "KAUF/ONLINE-SHOPPING VOM ...",
        // sometimes printed with the "KAUF" cut off as "/DIENSTLEISTUNG VOM ...".
        Regex("^(?:(?:APPLE|GOOGLE|SAMSUNG)\\s+PAY\\s+)?(?:KAUF)?/?(?:KAUF/DIENSTLEISTUNG|DIENSTLEISTUNG|ONLINE-SHOPPING|ONLINE-EINKAUF|EINKAUF|BARGELDBEZUG)\\s+VOM(?:\\s+\\d{2}\\.\\d{2}\\.\\d{4})?\\s*", RegexOption.IGNORE_CASE),
        Regex("KARTEN\\s*(?:NR\\.?|NUMMER)\\s*:?\\s*[X\\d]+\\s*", RegexOption.IGNORE_CASE),
        Regex("^TWINT\\s+GELD\\s+(?:SENDEN|EMPFANGEN)\\s+VOM(?:\\s+\\d{2}\\.\\d{2}\\.\\d{4})?\\s*", RegexOption.IGNORE_CASE),
        Regex("^(TWINT\\s+(KAUF|ZAHLUNG|GUTSCHRIFT)|E-BANKING-AUFTRAG|GUTSCHRIFT|LASTSCHRIFT|DAUERAUFTRAG|ÜBERTRAG|ZAHLUNG)\\s*(VOM\\s+\\d{2}\\.\\d{2}\\.\\d{4})?\\s*", RegexOption.IGNORE_CASE),
        // Credits name the payer after the (masked) account: "CH•• … 1765 ABSENDER: DIGITEC ...".
        Regex("^(?:[A-Z]{2}•• … [A-Z0-9]{4}\\s+)?(?:AUFTRAGGEBER|ABSENDER|SENDER|EMPFÄNGER|EMPFAENGER)\\s*:\\s*", RegexOption.IGNORE_CASE),
        Regex("\\s+(REFERENZ|MITTEILUNGEN?|REF\\.?)\\s*:?.*$", RegexOption.IGNORE_CASE),
        Regex("\\s{2,}"),
    )

    private val twintPerson = Regex("^TWINT\\s+GELD\\s+(SENDEN|EMPFANGEN)", RegexOption.IGNORE_CASE)

    /** "KAUF/DIENSTLEISTUNG VOM 28.08.2026 KARTEN NR. XXXX1234 COOP-1234 ZUERICH" → "COOP-1234 ZUERICH" */
    fun cleanPostFinance(text: String): String {
        var t = text.trim()
        for (r in pfNoise) t = r.replace(t, " ").trim()
        if (t.isEmpty()) return text.trim()
        // Keep "TWINT" in front of the person or shop, otherwise "+41 79 ... Name" says nothing.
        val twint = twintPerson.containsMatchIn(text.trim()) && !t.startsWith("TWINT", ignoreCase = true)
        return if (twint) "TWINT $t" else t
    }

    // ---------------------------------------------------------------- Revolut

    private fun revolut(lines: List<String>): ParseResult {
        val warnings = ArrayList<String>()
        val headerIdx = lines.indexOfFirst { it.lowercase().contains("completed date") }
        if (headerIdx < 0) return ParseResult("revolut", emptyList(), listOf("No Revolut header row found"))
        val header = split(lines[headerIdx], ',').map { it.lowercase().trim() }
        fun col(name: String) = header.indexOf(name)
        val iType = col("type"); val iStarted = col("started date"); val iCompleted = col("completed date")
        val iDesc = col("description"); val iAmount = col("amount"); val iFee = col("fee")
        val iCurrency = col("currency"); val iState = col("state"); val iBalance = col("balance")

        val rows = ArrayList<ParsedRow>()
        for ((offset, raw) in lines.drop(headerIdx + 1).withIndex()) {
            val lineNo = headerIdx + 2 + offset
            if (raw.isBlank()) continue
            val c = split(raw, ',')
            if (c.size <= iAmount) continue
            val state = c.getOrNull(iState)?.trim()?.uppercase()
            if (state != null && state != "COMPLETED") { warnings += "Line $lineNo: skipped ($state)"; continue }
            val dateText = c.getOrNull(iCompleted)?.takeIf { it.isNotBlank() } ?: c.getOrNull(iStarted) ?: continue
            val date = parseDate(dateText.trim().take(10), listOf("yyyy-MM-dd")) ?: continue
            val amount = number(c[iAmount]) ?: continue
            val fee = c.getOrNull(iFee)?.let { number(it) } ?: BigDecimal.ZERO
            // Revolut lists the fee separately; the account moved by amount minus fee.
            val net = amount - fee.abs()
            val kind = if (net.signum() >= 0) TransactionKind.INCOME else TransactionKind.EXPENSE
            val desc = c.getOrNull(iDesc)?.trim().orEmpty()
            val type = c.getOrNull(iType)?.trim().orEmpty()
            rows += ParsedRow(
                line = lineNo,
                date = date,
                description = desc.ifEmpty { type },
                rawDescription = listOf(type, desc).filter { it.isNotEmpty() }.joinToString(" · "),
                amount = Money.of(net.abs()),
                kind = kind,
                currency = c.getOrNull(iCurrency)?.trim()?.uppercase(),
                balanceAfter = c.getOrNull(iBalance)?.let { number(it) }?.let { Money.of(it) },
            )
        }
        return ParseResult("revolut", rows, warnings)
    }

    // ---------------------------------------------------------------- Generic

    private val anyDates = listOf("yyyy-MM-dd", "dd.MM.yyyy", "dd/MM/yyyy", "dd.MM.yy", "d.M.yyyy", "yyyy/MM/dd", "MM/dd/yyyy")

    /** Any table with a date column, a text column and either a signed amount or debit/credit columns. */
    private fun generic(lines: List<String>): ParseResult {
        val warnings = ArrayList<String>()
        val headerIdx = lines.indexOfFirst { it.isNotBlank() }
        if (headerIdx < 0) return ParseResult("generic", emptyList(), listOf("Empty file"))
        val headerLine = lines[headerIdx]
        val delimiter = listOf(';', ',', '\t').maxByOrNull { d -> headerLine.count { it == d } } ?: ','
        val header = split(headerLine, delimiter).map { it.lowercase().trim() }
        fun find(vararg keys: String) = header.indexOfFirst { h -> keys.any { h == it || h.contains(it) } }
        val iDate = find("date", "datum", "buchung", "valuta")
        val iText = find("description", "beschreibung", "text", "note", "memo", "payee", "empfänger", "details", "name")
        val iAmount = find("amount", "betrag", "value")
        val iCredit = find("credit", "gutschrift", "haben", "einnahme", "income", "in")
        val iDebit = find("debit", "lastschrift", "soll", "ausgabe", "expense", "out")
        val iCurrency = find("currency", "währung", "waehrung")
        if (iDate < 0 || (iAmount < 0 && (iCredit < 0 || iDebit < 0))) {
            return ParseResult("generic", emptyList(), listOf("Could not find date and amount columns in: ${header.joinToString(", ")}"))
        }
        val rows = ArrayList<ParsedRow>()
        for ((offset, raw) in lines.drop(headerIdx + 1).withIndex()) {
            val lineNo = headerIdx + 2 + offset
            if (raw.isBlank()) continue
            val c = split(raw, delimiter)
            val date = c.getOrNull(iDate)?.let { parseDate(it.trim().take(10), anyDates) } ?: run {
                warnings += "Line $lineNo: unreadable date"; null
            } ?: continue
            val (amount, kind) = if (iAmount >= 0) {
                val a = c.getOrNull(iAmount)?.let { number(it) } ?: continue
                a.abs() to if (a.signum() >= 0) TransactionKind.INCOME else TransactionKind.EXPENSE
            } else {
                val cr = c.getOrNull(iCredit)?.let { number(it) }
                val db = c.getOrNull(iDebit)?.let { number(it) }
                when {
                    cr != null && cr.signum() != 0 -> cr.abs() to TransactionKind.INCOME
                    db != null && db.signum() != 0 -> db.abs() to TransactionKind.EXPENSE
                    else -> continue
                }
            }
            val text = if (iText >= 0) c.getOrNull(iText)?.trim().orEmpty() else ""
            rows += ParsedRow(lineNo, date, text, text, Money.of(amount), kind, c.getOrNull(iCurrency)?.trim()?.uppercase()?.takeIf { it.length == 3 }, null)
        }
        return ParseResult("generic", rows, warnings)
    }

    // ---------------------------------------------------------------- helpers

    /** Splits one CSV line honouring double quotes (and doubled quotes inside them). */
    fun split(line: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> { out += cur.toString(); cur.setLength(0) }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    /** "1'234.50", "1.234,50", "-23.5", "CHF 12.00" → a number, or null when it is not one. */
    fun number(text: String): BigDecimal? {
        var t = text.trim().replace("'", "").replace("’", "").replace(" ", "").replace(Regex("[A-Za-z]{3}"), "")
        if (t.isEmpty() || t == "-") return null
        // Decide which of comma and dot is the decimal separator by whichever comes last.
        val lastComma = t.lastIndexOf(','); val lastDot = t.lastIndexOf('.')
        t = if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        return t.toBigDecimalOrNull()
    }

    fun parseDate(text: String, patterns: List<String>): LocalDate? {
        val t = text.trim().trim('"')
        for (p in patterns) {
            try { return LocalDate.parse(t, DateTimeFormatter.ofPattern(p)) } catch (_: Exception) {}
        }
        return null
    }
}
