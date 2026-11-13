package ch.sumry.core.privacy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AccountMaskingTest {

    @Test
    fun `masks a spaced IBAN to country code and last four`() {
        assertEquals("CH•• … 2957", maskIban("CH93 0076 2011 6238 5295 7"))
    }

    @Test
    fun `masks a packed IBAN`() {
        assertEquals("CH•• … 2957", maskIban("CH9300762011623852957"))
    }

    @Test
    fun `masks a lowercase IBAN and prints it uppercase`() {
        assertEquals("CH•• … 2957", maskIban("ch93 0076 2011 6238 5295 7"))
    }

    @Test
    fun `masks IBANs embedded in text and keeps the words around them`() {
        assertEquals(
            "GUTSCHRIFT VON CH•• … 8009 ARBEITGEBER AG, ref DE•• … 3000.",
            maskIban("GUTSCHRIFT VON CH56 0483 5012 3456 7800 9 ARBEITGEBER AG, ref DE89370400440532013000."),
        )
    }

    @Test
    fun `masks several IBANs in one text`() {
        assertEquals("from CH•• … 2957 to CH•• … 8009", maskIban("from CH9300762011623852957 to CH56 0483 5012 3456 7800 9"))
    }

    @Test
    fun `leaves ordinary text, card numbers, amounts and dates alone`() {
        for (t in listOf(
            "KAUF/DIENSTLEISTUNG VOM 01.10.2026 KARTEN NR. XXXX1234 MIGROS",
            "ab12 kauf coop migros",
            "1'234.50 CHF",
            "Rechnung 2026-10-01",
            "Ticket AB12345",
        )) assertEquals(t, maskAccounts(t))
    }

    @Test
    fun `masks Swiss postal account numbers`() {
        assertEquals("Konto ••-••••••-9 Miete", maskAccounts("Konto 12-345678-9 Miete"))
        assertEquals("••-••••••-3", maskAccounts("30-12-3"))
    }

    @Test
    fun `masking is idempotent`() {
        val once = maskAccounts("CH93 0076 2011 6238 5295 7 und 12-345678-9")
        assertEquals(once, maskAccounts(once))
    }

    @Test
    fun `null stays null`() {
        assertEquals(null, maskAccounts(null as String?))
    }
}
