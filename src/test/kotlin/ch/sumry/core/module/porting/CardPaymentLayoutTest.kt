package ch.sumry.core.module.porting

import ch.sumry.core.module.porting.PfLayout.booking
import ch.sumry.core.module.porting.PfLayout.header
import ch.sumry.core.types.TransactionKind
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Card and TWINT bookings that print the purchase date at the start of the second text line. */
class CardPaymentLayoutTest {

    private val account = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun read(pdf: ByteArray) = StatementReader.read(
        ImportPreviewRequest(account, "auto", fileBase64 = Base64.getEncoder().encodeToString(pdf), fileName = "auszug.pdf"),
    )

    private fun statement(): ByteArray = SyntheticPdf().page {
        text(40f, 150f, "Kontoauszug")
        text(40f, 174f, "Periode 01.10.2026 - 31.10.2026")
        header(210f)
        text(PfLayout.TEXT, 225f, "Kontostand")
        right(PfLayout.BALANCE, 225f, "500.00")
        var y = 240f
        y = booking(y, "03.10.26", listOf("APPLE PAY KAUF/DIENSTLEISTUNG VOM", "01.10.2026 KARTENNUMMER XXXX1111", "BEISPIEL BAECKEREI AG"), debit = "7.40", balance = "492.60")
        y = booking(y, "04.10.26", listOf("TWINT GELD SENDEN VOM", "04.10.2026", "AN +41 79 000 00 00 MUSTER ANNA"), debit = "20.00", balance = "472.60")
        y = booking(y, "06.10.26", listOf("KAUF/ONLINE-SHOPPING VOM", "05.10.2026 KARTENNUMMER XXXX1111", "EXAMPLE SHOP LTD"), debit = "12.30", balance = "460.30")
        y = booking(y, "07.10.26", listOf("KONTOÜBERTRAG AUF CH93 0076 2011 6238 5295 7"), debit = "100.00", balance = "360.30")
        text(PfLayout.TEXT, y + 5f, "Kontostand")
        right(PfLayout.BALANCE, y + 5f, "360.30")
    }.bytes()

    @Test
    fun `second-line purchase date continues the booking instead of starting one`() {
        val r = read(statement())
        assertEquals(4, r.rows.size, r.warnings.joinToString("\n"))
        assertTrue(r.warnings.none { "date but no amount" in it }, r.warnings.joinToString("\n"))
        val card = r.rows[0]
        assertEquals(LocalDate.of(2026, 10, 1), card.date)
        assertEquals(LocalDate.of(2026, 10, 3), card.bookingDate)
        assertEquals("BEISPIEL BAECKEREI AG", card.description)
        assertEquals("TWINT AN +41 79 000 00 00 MUSTER ANNA", r.rows[1].description)
        assertNull(r.rows[1].bookingDate)
        assertEquals("EXAMPLE SHOP LTD", r.rows[2].description)
        assertEquals(LocalDate.of(2026, 10, 5), r.rows[2].date)
    }

    @Test
    fun `rows add up from opening to closing balance`() {
        val r = read(statement())
        assertEquals(0, ImportMatcher.computedClosing(r.openingBalance, r.rows)!!.compareTo(r.closingBalance))
    }
}

class ImportMatcherTest {

    private fun row(date: String, amount: String, kind: TransactionKind = TransactionKind.EXPENSE, booking: String? = null) = ParsedRow(
        line = 0, date = LocalDate.parse(date), description = "x", rawDescription = "x",
        amount = BigDecimal(amount), kind = kind, currency = null, balanceAfter = null,
        bookingDate = booking?.let { LocalDate.parse(it) },
    )

    private fun entry(date: String, amount: String, kind: TransactionKind = TransactionKind.EXPENSE, label: String = "e") =
        ImportMatcher.Entry(UUID.randomUUID(), LocalDate.parse(date), BigDecimal(amount), kind, label)

    @Test
    fun `hand entry a day before the booking matches`() {
        val e = entry("2026-10-08", "229.35", label = "Furniture")
        val m = ImportMatcher.match(listOf(row("2026-10-09", "229.35")), listOf(e))
        assertEquals(listOf(e.id), m[0]!!.ids)
        assertEquals(ImportMatcher.Kind.NEAR, m[0]!!.kind)
        assertEquals("Furniture · 8 Oct", m[0]!!.note)
    }

    @Test
    fun `transfer out matches a bank debit`() {
        val e = entry("2026-10-23", "50.00", TransactionKind.TRANSFER_OUT)
        assertEquals(listOf(e.id), ImportMatcher.match(listOf(row("2026-10-23", "50.00")), listOf(e))[0]!!.ids)
    }

    @Test
    fun `income never matches an expense`() {
        val e = entry("2026-10-23", "50.00", TransactionKind.EXPENSE)
        assertTrue(ImportMatcher.match(listOf(row("2026-10-23", "50.00", TransactionKind.INCOME)), listOf(e)).isEmpty())
    }

    @Test
    fun `too far apart does not match`() {
        assertTrue(ImportMatcher.match(listOf(row("2026-10-20", "14.50")), listOf(entry("2026-10-14", "14.50"))).isEmpty())
    }

    @Test
    fun `booking date widens the window`() {
        val e = entry("2026-10-07", "9.00")
        assertEquals(1, ImportMatcher.match(listOf(row("2026-10-02", "9.00", booking = "2026-10-05")), listOf(e)).size)
    }

    @Test
    fun `each entry is used once and the closest row wins`() {
        val e = entry("2026-10-10", "3.70")
        val m = ImportMatcher.match(listOf(row("2026-10-12", "3.70"), row("2026-10-10", "3.70")), listOf(e))
        assertEquals(setOf(1), m.keys)
    }

    @Test
    fun `top-up plus fee is found as a split`() {
        val topUp = entry("2026-10-02", "100.00", TransactionKind.TRANSFER_OUT, "Top-up")
        val fee = entry("2026-10-02", "0.35", label = "Fee")
        val other = entry("2026-10-02", "6.00")
        val m = ImportMatcher.match(listOf(row("2026-10-03", "100.35")), listOf(topUp, fee, other))
        assertEquals(ImportMatcher.Kind.SPLIT, m[0]!!.kind)
        assertEquals(setOf(topUp.id, fee.id), m[0]!!.ids.toSet())
    }
}
