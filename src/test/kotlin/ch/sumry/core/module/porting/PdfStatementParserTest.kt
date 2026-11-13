package ch.sumry.core.module.porting

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.porting.PfLayout.booking
import ch.sumry.core.module.porting.PfLayout.header
import ch.sumry.core.types.TransactionKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdfStatementParserTest {

    private val account = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun read(pdf: ByteArray, format: String = "auto") = StatementReader.read(
        ImportPreviewRequest(account, format, fileBase64 = Base64.getEncoder().encodeToString(pdf), fileName = "kontoauszug.pdf"),
    )

    /** Two pages, letterhead with holder and IBAN, multi-line texts, Übertrag across the break, totals. */
    private fun postFinanceStatement(): ByteArray = SyntheticPdf()
        .page {
            text(40f, 40f, "PostFinance AG")
            text(330f, 90f, "Herr")
            text(330f, 101f, "Max Muster")
            text(330f, 112f, "Musterstrasse 12")
            text(330f, 123f, "8000 Zürich")
            text(40f, 150f, "Kontoauszug")
            text(40f, 162f, "Konto CH93 0076 2011 6238 5295 7")
            text(40f, 174f, "Periode 01.10.2026 - 31.10.2026")
            header(210f)
            text(PfLayout.TEXT, 225f, "Kontostand")
            right(PfLayout.BALANCE, 225f, "1'250.00")
            var y = 240f
            y = booking(y, "02.10.26", listOf("KAUF/DIENSTLEISTUNG VOM 01.10.2026", "KARTEN NR. XXXX1234", "MIGROS ZÜRICH"), debit = "23.50", balance = "1'226.50")
            y = booking(y, "05.10.26", listOf("GUTSCHRIFT VON CH56 0483 5012 3456 7800 9", "ARBEITGEBER AG LOHN OKTOBER"), credit = "3'450.00", balance = "4'676.50")
            y = booking(y, "05.10.26", listOf("TWINT ZAHLUNG", "Bäckerei Müller Größe Ä"), debit = "12.00", balance = "4'664.50")
            // Same day again: PostFinance may print the date only once.
            y = booking(y, null, listOf("E-BANKING-AUFTRAG", "Vermieterin Hanna Öztürk", "Konto 12-345678-9"), debit = "1'200.00", balance = "3'464.50")
            text(PfLayout.TEXT, 780f, "Übertrag")
            right(PfLayout.BALANCE, 780f, "3'464.50")
            text(40f, 805f, "PostFinance AG, Mingerstrasse 20, 3030 Bern")
            text(500f, 805f, "Seite 1/2")
        }
        .page {
            text(40f, 40f, "Kontoauszug")
            text(500f, 40f, "Seite 2/2")
            header(80f)
            text(PfLayout.TEXT, 95f, "Übertrag")
            right(PfLayout.BALANCE, 95f, "3'464.50")
            var y = 110f
            y = booking(y, "28.10.26", listOf("DAUERAUFTRAG", "SPARKONTO ÜBERWEISUNG"), debit = "50.00", balance = "3'414.50")
            y = booking(y, "31.10.26", listOf("ZINSABSCHLUSS"), credit = "0.35", balance = "3'414.85")
            text(PfLayout.TEXT, y + 5f, "Total")
            right(PfLayout.CREDIT, y + 5f, "3'450.35")
            right(PfLayout.DEBIT, y + 5f, "1'285.50")
            text(PfLayout.TEXT, y + 20f, "Kontostand per 31.10.2026")
            right(PfLayout.BALANCE, y + 20f, "3'414.85")
            text(40f, 805f, "PostFinance AG, Mingerstrasse 20, 3030 Bern")
        }
        .bytes()

    @Test
    fun `reads a two-page PostFinance statement`() {
        val r = read(postFinanceStatement())
        assertEquals(PdfStatementParser.FORMAT, r.format)
        assertEquals(6, r.rows.size, r.rows.joinToString("\n"))
        assertTrue(r.warnings.isEmpty(), r.warnings.joinToString("\n"))

        assertEquals(BigDecimal("1250.00"), r.openingBalance)
        assertEquals(BigDecimal("3414.85"), r.closingBalance)
        assertEquals(LocalDate.of(2026, 10, 1), r.statementFrom)
        assertEquals(LocalDate.of(2026, 10, 31), r.statementTo)

        val kinds = r.rows.map { it.kind }
        assertEquals(
            listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.EXPENSE, TransactionKind.EXPENSE, TransactionKind.EXPENSE, TransactionKind.INCOME),
            kinds,
        )
        assertEquals(listOf("23.50", "3450.00", "12.00", "1200.00", "50.00", "0.35"), r.rows.map { it.amount.toPlainString() })
        assertEquals(BigDecimal("3464.50"), r.rows[3].balanceAfter)
    }

    @Test
    fun `joins multi-line texts and keeps umlauts`() {
        val rows = read(postFinanceStatement()).rows
        assertEquals("MIGROS ZÜRICH", rows[0].description)
        assertEquals("KAUF/DIENSTLEISTUNG VOM 01.10.2026 KARTEN NR. XXXX1234 MIGROS ZÜRICH", rows[0].rawDescription)
        assertEquals("Bäckerei Müller Größe Ä", rows[2].description)
        assertEquals("DAUERAUFTRAG SPARKONTO ÜBERWEISUNG", rows[4].rawDescription)
    }

    @Test
    fun `a row without its own date takes the one above`() {
        val rows = read(postFinanceStatement()).rows
        assertEquals(LocalDate.of(2026, 10, 5), rows[3].date)
    }

    @Test
    fun `page footers and the Übertrag do not end up in a booking text`() {
        val rows = read(postFinanceStatement()).rows
        for (r in rows) {
            assertFalse(r.rawDescription.contains("Mingerstrasse"), r.rawDescription)
            assertFalse(r.rawDescription.contains("Seite"), r.rawDescription)
        }
        assertEquals("E-BANKING-AUFTRAG Vermieterin Hanna Öztürk Konto ••-••••••-9", rows[3].rawDescription)
    }

    @Test
    fun `masks the counterparty IBAN`() {
        val salary = read(postFinanceStatement()).rows[1]
        assertEquals("GUTSCHRIFT VON CH•• … 8009 ARBEITGEBER AG LOHN OKTOBER", salary.rawDescription)
        assertFalse(salary.description.contains("0483"))
    }

    @Test
    fun `explicit format works and a non-PDF is refused for it`() {
        assertEquals(6, read(postFinanceStatement(), "postfinance-pdf").rows.size)
        assertThrows<RequestException> { read("Datum;Text\n".toByteArray(), "postfinance-pdf") }
    }

    @Test
    fun `an amount printed in the wrong column is corrected from the balance`() {
        val pdf = SyntheticPdf().page {
            header(100f)
            text(PfLayout.TEXT, 115f, "Saldovortrag")
            right(PfLayout.BALANCE, 115f, "100.00")
            // A refund shown under Lastschrift although the balance went up.
            booking(130f, "03.10.2026", listOf("RÜCKERSTATTUNG"), debit = "10.00", balance = "110.00")
        }.bytes()
        val r = read(pdf)
        assertEquals(TransactionKind.INCOME, r.rows.single().kind)
        assertTrue(r.warnings.any { it.contains("direction corrected") })
    }

    @Test
    fun `an unknown layout without a header falls back to date text amount balance`() {
        val pdf = SyntheticPdf().page {
            text(40f, 100f, "Saldovortrag")
            right(400f, 100f, "1'000.00")
            text(40f, 115f, "02.10.2026"); text(100f, 115f, "Coop Pronto Bern"); right(330f, 115f, "23.50"); right(400f, 115f, "976.50")
            text(40f, 130f, "04.10.2026"); text(100f, 130f, "Rückzahlung Lea"); right(330f, 130f, "40.00"); right(400f, 130f, "1'016.50")
        }.bytes()
        val r = read(pdf)
        assertEquals(2, r.rows.size)
        assertEquals(listOf(TransactionKind.EXPENSE, TransactionKind.INCOME), r.rows.map { it.kind })
        assertEquals(BigDecimal("1000.00"), r.openingBalance)
        assertEquals(BigDecimal("1016.50"), r.closingBalance)
        assertTrue(r.warnings.first().startsWith("No column header found"))
    }

    @Test
    fun `an unreadable PDF answers with redacted diagnostics instead of rows`() {
        val pdf = SyntheticPdf().page {
            text(40f, 40f, "Some Other Bank")
            text(40f, 60f, "Kontoinhaber: Max Muster")
            text(40f, 71f, "Musterstrasse 12")
            text(40f, 82f, "8000 Zürich")
            text(40f, 100f, "IBAN CH93 0076 2011 6238 5295 7")
            text(40f, 111f, "Kundennummer 123 456 789")
            text(40f, 130f, "Buchung vom 02.10.2026 über Franken 23.50 an Coop")
        }.bytes()
        val r = read(pdf)
        assertTrue(r.rows.isEmpty())
        val diag = r.warnings.single { it.startsWith("Diagnostics") }
        assertFalse(diag.contains("Muster"), diag)
        assertFalse(diag.contains("8000"), diag)
        assertFalse(diag.contains("6238"), diag)
        assertFalse(diag.contains("123 456 789"), diag)
        assertTrue(diag.contains("CH•• … 2957"), diag)
        assertTrue(diag.contains("Some Other Bank"), diag)
        assertTrue(diag.contains("[p1 x40]"), diag)
    }

    @Test
    fun `garbage is refused with a plain message`() {
        val ex = assertThrows<RequestException> { read("%PDF-1.7 not really a pdf".toByteArray()) }
        assertEquals("Not a readable PDF file", ex.message)
    }

    @Test
    fun `extraction sorts words into lines by position`() {
        val lines = PdfStatementParser.extractLines(postFinanceStatement())
        val header = lines.first { it.text.startsWith("Datum") }
        assertEquals("Datum Text Gutschrift Lastschrift Valuta Saldo", header.text)
        assertNotNull(lines.firstOrNull { it.text == "02.10.26 KAUF/DIENSTLEISTUNG VOM 01.10.2026 23.50 02.10.26 1'226.50" })
    }
}
