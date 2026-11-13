package ch.sumry.core.module.porting

import ch.sumry.core.exception.RequestException
import ch.sumry.core.types.Money
import ch.sumry.core.types.TransactionKind
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.springframework.http.HttpStatus
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/**
 * The monthly PostFinance "Kontoauszug" PDF: a table with the columns
 * Datum · Text · Gutschrift · Lastschrift · Valuta · Saldo, where a booking text runs over
 * several lines (only the first carries date and amounts) and every page ends and starts
 * with an "Übertrag" carrying the running balance.
 *
 * The text is extracted with positions. When the header row is found, each amount is put
 * in the column whose heading sits above it, so a credit is never read as a debit. Without
 * a header (an unknown layout) the parser falls back to reading rows as
 * "date · text · amount [· balance]" and works out the direction from the running balance.
 * Every booking is checked against the balance column when there is one, and anything that
 * looked like a booking but could not be read ends up as a warning, never silently dropped.
 */
object PdfStatementParser {

    const val FORMAT = "postfinance-pdf"
    private const val MAX_PAGES = 300
    /** Words whose baselines differ by less than this are on the same line. */
    private const val LINE_TOLERANCE = 3f
    /** A booking text continues for at most this many lines below its first. */
    private const val MAX_CONTINUATION = 8
    /** ...and no further than this below the line before (about two and a half lines). */
    private const val CONTINUATION_GAP = 30f

    data class Word(val text: String, val x: Float, val xEnd: Float, val y: Float)

    data class Line(val no: Int, val page: Int, val y: Float, val words: List<Word>) {
        val text: String get() = words.joinToString(" ") { it.text }
        val x: Float get() = words.first().x
    }

    fun isPdf(bytes: ByteArray): Boolean {
        // The header may follow a little junk; readers accept it anywhere in the first KB.
        val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
        return head.contains("%PDF-")
    }

    fun parse(bytes: ByteArray): ParseResult = parseLines(extractLines(bytes))

    // ------------------------------------------------------------- extraction

    fun extractLines(bytes: ByteArray): List<Line> {
        val words = ArrayList<Pair<Int, Word>>()
        try {
            Loader.loadPDF(bytes).use { doc ->
                if (doc.numberOfPages > MAX_PAGES) {
                    throw RequestException(HttpStatus.BAD_REQUEST, "PDF has ${doc.numberOfPages} pages; at most $MAX_PAGES are read")
                }
                val stripper = object : PDFTextStripper() {
                    override fun writeString(text: String, positions: MutableList<TextPosition>) {
                        for (w in splitWords(positions)) words += currentPageNo to w
                    }
                }
                stripper.sortByPosition = true
                stripper.getText(doc)
            }
        } catch (_: InvalidPasswordException) {
            throw RequestException(HttpStatus.BAD_REQUEST, "The PDF is password-protected; download it again without a password")
        } catch (e: RequestException) {
            throw e
        } catch (_: IOException) {
            throw RequestException(HttpStatus.BAD_REQUEST, "Not a readable PDF file")
        }
        return groupLines(words)
    }

    /** PDFBox hands over runs that may contain spaces; split them into words that keep their positions. */
    private fun splitWords(positions: List<TextPosition>): List<Word> {
        val out = ArrayList<Word>()
        var cur = ArrayList<TextPosition>()
        fun flush() {
            if (cur.isEmpty()) return
            val text = cur.joinToString("") { it.unicode }.trim()
            if (text.isNotEmpty()) {
                val last = cur.last()
                out += Word(text, cur.first().xDirAdj, last.xDirAdj + last.widthDirAdj, cur.first().yDirAdj)
            }
            cur = ArrayList()
        }
        for (p in positions) {
            if (p.unicode.isBlank()) { flush(); continue }
            val prev = cur.lastOrNull()
            if (prev != null) {
                val gap = p.xDirAdj - (prev.xDirAdj + prev.widthDirAdj)
                val space = max(prev.widthOfSpace, 1f)
                if (gap > space * 0.6f || abs(p.yDirAdj - prev.yDirAdj) > LINE_TOLERANCE) flush()
            }
            cur += p
        }
        flush()
        return out
    }

    private fun groupLines(words: List<Pair<Int, Word>>): List<Line> {
        val lines = ArrayList<Line>()
        val sorted = words.sortedWith(compareBy({ it.first }, { it.second.y }, { it.second.x }))
        var page = -1
        var y = 0f
        var current = ArrayList<Word>()
        fun flush() {
            if (current.isNotEmpty()) lines += Line(lines.size + 1, page, y, current.sortedBy { it.x })
            current = ArrayList()
        }
        for ((p, w) in sorted) {
            if (p != page || w.y - y > LINE_TOLERANCE) { flush(); page = p; y = w.y }
            current += w
        }
        flush()
        return lines
    }

    // ---------------------------------------------------------------- parsing

    private data class Span(val x: Float, val xEnd: Float) {
        fun distance(w: Word): Float = max(0f, max(x - w.xEnd, w.x - xEnd))
        fun centreDistance(w: Word): Float = abs((x + xEnd) / 2 - (w.x + w.xEnd) / 2)
    }

    private enum class Col { CREDIT, DEBIT, VALUTA, BALANCE }

    private data class Columns(val date: Span, val text: Span?, val spans: Map<Col, Span>) {
        /** Left edge of the amount area: anything ending right of it is a figure, not text. */
        val amountArea: Float = spans.values.minOf { it.x } - 4f

        fun columnOf(w: Word): Col = spans.entries.minWith(
            compareBy<Map.Entry<Col, Span>> { it.value.distance(w) }.thenBy { it.value.centreDistance(w) },
        ).key
    }

    private fun header(line: Line): Columns? {
        if (line.words.any { isAmount(it) }) return null
        fun find(vararg keys: String) = line.words.firstOrNull { w -> keys.any { w.text.lowercase().startsWith(it) } }
        val date = find("datum", "date", "buchung") ?: return null
        val credit = find("gutschrift", "credit", "haben", "crédit", "accredit")
        val debit = find("lastschrift", "belastung", "debit", "soll", "débit", "addebit")
        if (credit == null && debit == null) return null
        val spans = buildMap {
            credit?.let { put(Col.CREDIT, Span(it.x, it.xEnd)) }
            debit?.let { put(Col.DEBIT, Span(it.x, it.xEnd)) }
            find("valuta", "value", "valeur")?.let { put(Col.VALUTA, Span(it.x, it.xEnd)) }
            find("saldo", "balance", "solde", "kontostand")?.let { put(Col.BALANCE, Span(it.x, it.xEnd)) }
        }
        val text = find("text", "avisierungstext", "buchungstext", "beschreibung", "details", "description", "libellé", "testo")
        return Columns(Span(date.x, date.xEnd), text?.let { Span(it.x, it.xEnd) }, spans)
    }

    private val dateRe = Regex("^[0-3]?[0-9]\\.[01]?[0-9]\\.([0-9]{4}|[0-9]{2})$")
    private val amountRe = Regex("^[-−]?[0-9]{1,3}(?:['’ ]?[0-9]{3})*[.,][0-9]{2}-?$")
    /** Lines that only carry a balance, whatever else is printed on them. */
    private val balanceLine = Regex(
        "^(übertrag|uebertrag|saldovortrag|kontostand|anfangssaldo|schlusssaldo|endsaldo|report|opening balance|closing balance|balance carried|balance brought)(?![\\p{L}])",
    )
    /** "Saldo" on its own is a balance line only when there is no credit or debit next to it. */
    private val weakBalanceLine = Regex("^(neuer saldo|alter saldo|saldo|solde)(?![\\p{L}])")
    private val totalLine = Regex("^(total|umsatztotal|zwischentotal|summe|totaux|totale)(?![\\p{L}])")
    private val incomeHints = Regex("gutschrift|eingang|lohn|salär|salaer|rückerstattung|rueckerstattung|zins|credit", RegexOption.IGNORE_CASE)
    private val periodRe = Regex("([0-3]?[0-9]\\.[01]?[0-9]\\.(?:[0-9]{4}|[0-9]{2}))\\s*(?:-|–|bis|to|au)\\s*([0-3]?[0-9]\\.[01]?[0-9]\\.(?:[0-9]{4}|[0-9]{2}))")

    private fun date(text: String): LocalDate? = CsvParsers.parseDate(text, listOf("d.M.yyyy", "d.M.yy"))

    private fun amount(text: String): BigDecimal? {
        val negative = text.startsWith("-") || text.startsWith("−") || text.endsWith("-")
        val n = CsvParsers.number(text.trim('-', '−')) ?: return null
        return Money.of(if (negative) n.negate() else n)
    }

    private fun isAmount(w: Word) = amountRe.matches(w.text)
    private fun isDate(w: Word) = dateRe.matches(w.text) && date(w.text) != null

    private class Open(
        val start: Line,
        val date: LocalDate,
        val amount: BigDecimal,
        var kind: TransactionKind,
        val balance: BigDecimal?,
        val text: StringBuilder,
        val textX: Float,
        var lastY: Float,
        var continuation: Int = 0,
    )

    fun parseLines(lines: List<Line>): ParseResult {
        val warnings = ArrayList<String>()
        val rows = ArrayList<ParsedRow>()
        val firstHeaderIdx = lines.indexOfFirst { header(it) != null }
        val positional = firstHeaderIdx >= 0
        var columns: Columns? = null
        var opening: BigDecimal? = null
        var lastBalanceLine: BigDecimal? = null
        var lastBalanceAfterRows = false
        var running: BigDecimal? = null
        var lastDate: LocalDate? = null
        var open: Open? = null
        var unparsed = 0

        fun close() {
            val o = open ?: return
            open = null
            val raw = o.text.toString().replace(Regex("\\s+"), " ").trim()
            // Check the booking against the balance column; a mismatch that the other
            // direction explains means the amount sat in the wrong column.
            var kind = o.kind
            if (running != null && o.balance != null) {
                val plus = Money.of(running!! + o.amount)
                val minus = Money.of(running!! - o.amount)
                val expected = if (kind == TransactionKind.INCOME) plus else minus
                val other = if (kind == TransactionKind.INCOME) minus else plus
                if (expected.compareTo(o.balance) != 0) {
                    if (other.compareTo(o.balance) == 0) {
                        kind = if (kind == TransactionKind.INCOME) TransactionKind.EXPENSE else TransactionKind.INCOME
                        warnings += "Line ${o.start.no}: direction corrected from the balance column"
                    } else {
                        warnings += "Line ${o.start.no}: balance does not add up (check this row)"
                    }
                }
            }
            running = o.balance ?: running?.let { if (kind == TransactionKind.INCOME) Money.of(it + o.amount) else Money.of(it - o.amount) }
            rows += ParsedRow(
                line = o.start.no,
                date = o.date,
                description = CsvParsers.cleanPostFinance(raw).ifEmpty { raw },
                rawDescription = raw,
                amount = o.amount,
                kind = kind,
                currency = null,
                balanceAfter = o.balance,
            )
            lastBalanceAfterRows = false
        }

        for ((idx, line) in lines.withIndex()) {
            if (open != null && line.page != open!!.start.page) close()
            val h = header(line)
            if (h != null) { close(); columns = h; continue }
            // Everything above the first header is letterhead: address, account, period.
            if (positional && idx < firstHeaderIdx) continue

            val words = line.words
            val leadingDate = words.first().takeIf { isDate(it) }?.let { date(it.text) }
            val body = if (leadingDate != null) words.drop(1) else words
            val cols = columns

            // The figures at the end of the line: amounts and the valuta date.
            var cut = body.size
            while (cut > 0) {
                val w = body[cut - 1]
                if (!(isAmount(w) || isDate(w))) break
                if (cols != null && w.xEnd < cols.amountArea) break
                cut--
            }
            var tail = body.subList(cut, body.size)
            if (tail.none { isAmount(it) }) { cut = body.size; tail = emptyList() }
            val textWords = body.subList(0, cut)
            val text = textWords.joinToString(" ") { it.text }
            val lower = text.lowercase()
            val amounts = tail.filter { isAmount(it) }

            // Balance and total lines carry figures but are not bookings.
            if (totalLine.containsMatchIn(lower)) { close(); continue }
            val bookingFigures = if (cols != null) amounts.count { cols.columnOf(it) == Col.CREDIT || cols.columnOf(it) == Col.DEBIT }
            else amounts.size - 1
            val isBalance = balanceLine.containsMatchIn(lower) || (weakBalanceLine.containsMatchIn(lower) && bookingFigures <= 0)
            if (amounts.isNotEmpty() && isBalance) {
                close()
                val balanceWord = cols?.let { c -> amounts.firstOrNull { c.columnOf(it) == Col.BALANCE } } ?: amounts.last()
                val value = amount(balanceWord.text)
                if (value != null) {
                    if (opening == null && rows.isEmpty()) opening = value
                    lastBalanceLine = value
                    lastBalanceAfterRows = true
                    running = value
                }
                continue
            }

            if (amounts.isNotEmpty()) {
                close()
                val bookingDate = leadingDate ?: lastDate
                if (bookingDate == null) { unparsed++; warnings += "Line ${line.no}: amount without a date"; continue }
                var credit: BigDecimal? = null
                var debit: BigDecimal? = null
                var balance: BigDecimal? = null
                var signed: BigDecimal? = null
                if (cols != null) {
                    for (w in amounts) when (cols.columnOf(w)) {
                        Col.CREDIT -> credit = amount(w.text)
                        Col.DEBIT -> debit = amount(w.text)
                        Col.BALANCE -> balance = amount(w.text)
                        Col.VALUTA -> {}
                    }
                } else {
                    val values = amounts.mapNotNull { amount(it.text) }
                    if (values.size >= 2) { signed = values[values.size - 2]; balance = values.last() } else signed = values.firstOrNull()
                    if (values.size > 2) warnings += "Line ${line.no}: more than two amounts, used the last two"
                }
                val (value, kind) = when {
                    credit != null && debit != null && credit!!.signum() != 0 && debit!!.signum() != 0 -> {
                        unparsed++; warnings += "Line ${line.no}: both a credit and a debit - skipped"; continue
                    }
                    credit != null && credit!!.signum() != 0 -> credit!!.abs() to TransactionKind.INCOME
                    debit != null && debit!!.signum() != 0 -> debit!!.abs() to TransactionKind.EXPENSE
                    signed != null -> {
                        val v = signed!!
                        val k = when {
                            v.signum() < 0 -> TransactionKind.EXPENSE
                            balance != null && running != null && Money.of(running!! + v).compareTo(balance) == 0 -> TransactionKind.INCOME
                            balance != null && running != null && Money.of(running!! - v).compareTo(balance) == 0 -> TransactionKind.EXPENSE
                            else -> {
                                warnings += "Line ${line.no}: direction guessed from the text"
                                if (incomeHints.containsMatchIn(text)) TransactionKind.INCOME else TransactionKind.EXPENSE
                            }
                        }
                        v.abs() to k
                    }
                    else -> { unparsed++; warnings += "Line ${line.no}: figures but no credit or debit amount"; continue }
                }
                lastDate = bookingDate
                open = Open(
                    start = line,
                    date = bookingDate,
                    amount = Money.of(value),
                    kind = kind,
                    balance = balance,
                    text = StringBuilder(text),
                    textX = textWords.firstOrNull()?.x ?: cols?.text?.x ?: line.x,
                    lastY = line.y,
                )
                continue
            }

            // No figures: either more booking text for the open row, or page furniture.
            val o = open
            if (o != null && leadingDate == null && o.continuation < MAX_CONTINUATION) {
                val near = line.y - o.lastY < CONTINUATION_GAP
                val inTextColumn = if (cols != null) line.x >= (cols.text?.x ?: cols.date.xEnd) - 6f && line.x < cols.amountArea
                else line.x > o.start.x + 4f
                if (near && inTextColumn) {
                    o.text.append(' ').append(line.text)
                    o.lastY = line.y
                    o.continuation++
                    continue
                }
            }
            close()
            if (leadingDate != null && text.isNotBlank()) { unparsed++; warnings += "Line ${line.no}: date but no amount" }
        }
        close()

        val period = lines.asSequence().mapNotNull { periodRe.find(it.text) }.firstOrNull()
        val from = period?.groupValues?.get(1)?.let { date(it) } ?: rows.minOfOrNull { it.date }
        val to = period?.groupValues?.get(2)?.let { date(it) } ?: rows.maxOfOrNull { it.date }
        val closing = if (lastBalanceAfterRows) lastBalanceLine else rows.lastOrNull()?.balanceAfter ?: running

        if (!positional) warnings.add(0, "No column header found; read the rows by position, check the directions")
        if (rows.isEmpty() || unparsed > 0) {
            val prefixes = lines.map { "[p${it.page} x${it.x.toInt()}] " }
            warnings += StatementDiagnostics.block("PDF text", lines.map { it.text }, prefixes)
        }
        return ParseResult(FORMAT, rows, warnings, from, to, opening, closing)
    }
}
