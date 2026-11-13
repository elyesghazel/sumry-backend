package ch.sumry.core.module.porting

import ch.sumry.core.exception.RequestException
import ch.sumry.core.types.Money
import ch.sumry.core.types.TransactionKind
import org.springframework.http.HttpStatus
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * ISO 20022 cash-management XML, as PostFinance e-finance offers it next to the PDF:
 * camt.053 (statement, the "Kontoauszug"), camt.052 (intraday report) and camt.054
 * (debit/credit notification). The three share the entry structure; only the container
 * and the presence of balances differ.
 *
 * Elements are matched by local name so every schema version (.001.02 to .001.13 and
 * the Swiss SPS flavours) reads the same.
 */
object CamtParser {

    fun looksLikeCamt(text: String): Boolean {
        val head = text.take(4096)
        return head.contains("camt.05") || head.contains("BkToCstmr")
    }

    fun parse(bytes: ByteArray): ParseResult {
        val doc = try {
            factory().newDocumentBuilder().apply { setErrorHandler(Silent) }.parse(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            // The parser's message can quote the file, so it is not passed on.
            throw RequestException(HttpStatus.BAD_REQUEST, "Not a readable camt XML file")
        }
        val root = doc.documentElement
        val container = root.elements().firstOrNull { it.localName.startsWith("BkToCstmr") }
            ?: throw RequestException(HttpStatus.BAD_REQUEST, "XML file is not a camt.052/053/054 statement")
        val (format, reportName) = when (container.localName) {
            "BkToCstmrStmt" -> "camt053" to "Stmt"
            "BkToCstmrAcctRpt" -> "camt052" to "Rpt"
            else -> "camt054" to "Ntfctn"
        }
        val reports = container.kids(reportName)
        val warnings = ArrayList<String>()
        if (reports.size > 1) warnings += "The file holds ${reports.size} statements; all their entries are listed"

        val rows = ArrayList<ParsedRow>()
        var opening: BigDecimal? = null
        var closing: BigDecimal? = null
        var from: LocalDate? = null
        var to: LocalDate? = null
        var skippedPending = 0
        var entryNo = 0

        for (report in reports) {
            val accountCurrency = report.text("Acct", "Ccy")
            for (bal in report.kids("Bal")) {
                val code = bal.text("Tp", "CdOrPrtry", "Cd") ?: bal.text("Tp", "CdOrPrtry", "Prtry")
                val amount = bal.text("Amt")?.toBigDecimalOrNull() ?: continue
                val signed = Money.of(if (bal.text("CdtDbtInd") == "DBIT") amount.negate() else amount)
                when (code) {
                    "OPBD", "PRCD" -> if (opening == null) opening = signed
                    "CLBD" -> closing = signed
                }
            }
            report.path("FrToDt")?.let { period ->
                period.text("FrDtTm")?.let { date(it) }?.let { if (from == null || it < from) from = it }
                period.text("ToDtTm")?.let { date(it) }?.let { if (to == null || it > to) to = it }
            }

            for (entry in report.kids("Ntry")) {
                entryNo++
                val status = entry.text("Sts", "Cd") ?: entry.text("Sts")
                if (status != null && status != "BOOK") { skippedPending++; continue }
                val amountEl = entry.kid("Amt")
                val amount = amountEl?.textContent?.trim()?.toBigDecimalOrNull()
                val date = (entry.text("BookgDt", "Dt") ?: entry.text("BookgDt", "DtTm")
                    ?: entry.text("ValDt", "Dt") ?: entry.text("ValDt", "DtTm"))?.let { date(it) }
                val direction = entry.text("CdtDbtInd")
                if (amount == null || date == null || (direction != "CRDT" && direction != "DBIT")) {
                    warnings += "Entry $entryNo: missing amount, date or direction - skipped"
                    continue
                }
                val kind = if (direction == "CRDT") TransactionKind.INCOME else TransactionKind.EXPENSE
                val (description, raw) = describe(entry, kind)
                rows += ParsedRow(
                    line = entryNo,
                    date = date,
                    description = description,
                    rawDescription = raw,
                    amount = Money.of(amount.abs()),
                    kind = kind,
                    currency = amountEl.getAttribute("Ccy").ifBlank { accountCurrency },
                    balanceAfter = null,
                )
            }
        }
        if (skippedPending > 0) warnings += "$skippedPending pending (not yet booked) entries skipped"
        if (rows.isEmpty() || warnings.any { it.startsWith("Entry ") }) warnings += diagnostics(bytes)
        return ParseResult(
            format = format,
            rows = rows,
            warnings = warnings,
            statementFrom = from ?: rows.minOfOrNull { it.date },
            statementTo = to ?: rows.maxOfOrNull { it.date },
            openingBalance = opening,
            closingBalance = closing,
        )
    }

    /** Counterparty and remittance text; a card or batch booking falls back to the bank's own entry text. */
    private fun describe(entry: Element, kind: TransactionKind): Pair<String, String> {
        val details = entry.kids("NtryDtls").flatMap { it.kids("TxDtls") }
        val party = if (kind == TransactionKind.INCOME) "Dbtr" else "Cdtr"
        val ultimate = if (kind == TransactionKind.INCOME) "UltmtDbtr" else "UltmtCdtr"
        val names = details.mapNotNull { tx ->
            val parties = tx.kid("RltdPties") ?: return@mapNotNull null
            parties.text(party, "Nm") ?: parties.text(party, "Pty", "Nm")
                ?: parties.text(ultimate, "Nm") ?: parties.text(ultimate, "Pty", "Nm")
        }.distinct()
        val remittance = details.flatMap { tx -> tx.kid("RmtInf")?.kids("Ustrd").orEmpty().mapNotNull { it.textContent?.trim() } }
            .filter { it.isNotEmpty() }.distinct()
        val txInfo = details.mapNotNull { it.text("AddtlTxInf") }.distinct()
        val entryInfo = entry.text("AddtlNtryInf")

        val who = when {
            names.isEmpty() -> null
            names.size <= 3 -> names.joinToString(", ")
            else -> names.take(3).joinToString(", ") + " +${names.size - 3}"
        }
        val description = listOfNotNull(who, remittance.joinToString(" ").takeIf { it.isNotEmpty() })
            .joinToString(" · ")
            .ifEmpty { entryInfo?.let { CsvParsers.cleanPostFinance(it) } ?: txInfo.firstOrNull() ?: "" }
        val raw = (listOfNotNull(entryInfo) + names + remittance + txInfo).distinct().joinToString(" · ")
        return description.collapse() to raw.collapse()
    }

    /** The element structure with every value hidden except codes, dates and amounts. */
    private fun diagnostics(bytes: ByteArray): String {
        val keep = setOf("Cd", "CdtDbtInd", "Dt", "DtTm", "FrDtTm", "ToDtTm", "Sts", "Amt", "RvslInd", "Ccy", "NbOfNtries")
        val xml = String(bytes, Charsets.UTF_8)
        val values = Regex("<((?:[\\w-]+:)?([\\w-]+))(\\s[^>]*)?>([^<]+)</\\1>")
        val masked = values.replace(xml) { m ->
            val (tag, local, attrs, value) = m.destructured
            if (local in keep || value.isBlank()) m.value else "<$tag$attrs>…</$tag>"
        }
        val lines = masked.replace(Regex(">\\s*<"), ">\n<").lines().map { it.trim() }.filter { it.isNotEmpty() }
        return StatementDiagnostics.block("camt XML", lines)
    }

    /** The default handler prints parse errors (and with them bits of the file) to stderr. */
    private object Silent : ErrorHandler {
        override fun warning(e: SAXParseException) {}
        override fun error(e: SAXParseException) = throw e
        override fun fatalError(e: SAXParseException) = throw e
    }

    private fun factory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        // No DTDs and no external anything: a statement upload must not be able to read files or reach the network (XXE).
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        isXIncludeAware = false
        isExpandEntityReferences = false
        isNamespaceAware = true
    }

    private fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim().take(10)) }.getOrNull()

    private fun String.collapse() = replace(Regex("\\s+"), " ").trim()

    private fun Node.elements(): List<Element> {
        val out = ArrayList<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let { out += it }
        return out
    }

    private fun Element.kids(name: String): List<Element> = elements().filter { (it.localName ?: it.tagName) == name }
    private fun Element.kid(name: String): Element? = kids(name).firstOrNull()
    private fun Element.path(vararg names: String): Element? {
        var cur: Element? = this
        for (n in names) cur = cur?.kid(n) ?: return null
        return cur
    }
    private fun Element.text(vararg names: String): String? = path(*names)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
