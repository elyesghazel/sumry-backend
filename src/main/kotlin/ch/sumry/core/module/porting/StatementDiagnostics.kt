package ch.sumry.core.module.porting

import ch.sumry.core.privacy.maskAccounts

/**
 * When a statement cannot be read, the user gets the first lines of what the parser saw so
 * they can hand it to someone (or an agent) to adapt the parser. Those lines are redacted
 * first: account numbers, the holder's name and address, e-mails and long digit runs never
 * leave in the clear. Dates, amounts, column headings and booking texts stay, since they
 * are what a layout fix needs.
 */
object StatementDiagnostics {

    const val MAX_LINES = 40
    private const val MAX_LINE_LENGTH = 160
    private const val HIDDEN = "[hidden]"

    /** Labels after which the next lines are the holder's name and address. */
    private val holderLabel = Regex(
        "(konto)?inhaber(in)?|account holder|titulaire|titolare|zugunsten|kunde\\b|customer|^\\s*(herr|frau|familie|monsieur|madame|mr\\.?|mrs\\.?|ms\\.?)(\\s|$)",
        RegexOption.IGNORE_CASE,
    )
    /** Lines that start a new block, so the holder's address has ended. */
    private val blockEnd = Regex("iban|konto|account|compte|conto|datum|date|periode|seite|page|kunden", RegexOption.IGNORE_CASE)
    private val postcodeCity = Regex("^\\s*(CH-|LI-)?[1-9][0-9]{3}\\s+\\p{L}")
    private val street = Regex(
        "\\p{L}*(strasse|str\\.|gasse|weg|platz|allee|rain|ring|quai|halde|matte|rue|chemin|avenue|via)\\s*[0-9]+\\s*\\p{L}?\\b",
        RegexOption.IGNORE_CASE,
    )
    private val poBox = Regex("(postfach|case postale|casella postale|p\\.?o\\.? box)\\s*[0-9]*", RegexOption.IGNORE_CASE)
    private val email = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    /** Card, customer, reference and phone numbers: seven or more digits, spaces allowed. */
    private val longDigits = Regex("(?<![0-9])(?<![0-9][.,'’])\\+?[0-9](?:[ ]?[0-9]){6,}(?![0-9]|[.,'’][0-9])")
    private val looksLikeTableRow = Regex("[0-9]{2}\\.[0-9]{2}\\.[0-9]{2,4}|[0-9]\\.[0-9]{2}(?![0-9])")

    /**
     * Redacts [lines] and wraps the first [MAX_LINES] into one warning. [prefixes] (page and
     * position, say) are prepended after redaction, so they never hide a match.
     */
    fun block(title: String, lines: List<String>, prefixes: List<String> = emptyList()): String {
        val shown = redact(lines.take(MAX_LINES))
        return buildString {
            append("Diagnostics, redacted ($title, first ${shown.size} of ${lines.size} lines):")
            for ((i, l) in shown.withIndex()) append('\n').append(prefixes.getOrNull(i).orEmpty()).append(l)
        }
    }

    fun redact(lines: List<String>): List<String> {
        val hide = BooleanArray(lines.size)
        var holderLinesLeft = 0
        for ((i, line) in lines.withIndex()) {
            val tableRow = looksLikeTableRow.containsMatchIn(line)
            when {
                holderLabel.containsMatchIn(line) -> { hide[i] = true; holderLinesLeft = 3 }
                holderLinesLeft > 0 && !tableRow && line.isNotBlank() && !blockEnd.containsMatchIn(line) -> { hide[i] = true; holderLinesLeft-- }
                else -> holderLinesLeft = 0
            }
            if (postcodeCity.containsMatchIn(line) && !tableRow) {
                hide[i] = true
                // The two lines above a postcode line are the street and the name.
                for (j in (i - 2).coerceAtLeast(0) until i) {
                    if (!looksLikeTableRow.containsMatchIn(lines[j]) && lines[j].length < 60) hide[j] = true
                }
            }
            if (street.containsMatchIn(line) || poBox.containsMatchIn(line)) hide[i] = true
        }
        return lines.mapIndexed { i, line ->
            if (hide[i]) {
                // Keep a recognisable label so the structure is still visible.
                val label = Regex("^[^:]{1,30}:").find(line)?.value?.takeIf { holderLabel.containsMatchIn(it) }
                listOfNotNull(label, HIDDEN).joinToString(" ")
            } else {
                redactText(line)
            }
        }
    }

    /** Masks what can be spotted inside a single line, wherever it appears. */
    fun redactText(text: String): String {
        var t = maskAccounts(text)
        t = email.replace(t, "[email]")
        t = longDigits.replace(t, "[number]")
        return if (t.length > MAX_LINE_LENGTH) t.take(MAX_LINE_LENGTH) + "…" else t
    }
}
