package ch.sumry.core.module.porting

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatementDiagnosticsTest {

    @Test
    fun `hides the holder block, address, account numbers and long digit runs`() {
        val lines = listOf(
            "PostFinance AG",
            "Kontoinhaberin: Erika Beispiel",
            "c/o Familie Beispiel",
            "Beispielweg 7a",
            "3000 Bern",
            "IBAN CH93 0076 2011 6238 5295 7",
            "Postkonto 12-345678-9",
            "Telefon +41 79 123 45 67, erika@example.ch",
            "Datum Text Gutschrift Lastschrift Valuta Saldo",
            "02.10.26 KAUF/DIENSTLEISTUNG VOM 01.10.2026 23.50 02.10.26 1'226.50",
        )
        val out = StatementDiagnostics.redact(lines)
        assertEquals("PostFinance AG", out[0])
        assertEquals("Kontoinhaberin: [hidden]", out[1])
        assertEquals("[hidden]", out[2])
        assertEquals("[hidden]", out[3])
        assertEquals("[hidden]", out[4])
        assertEquals("IBAN CH•• … 2957", out[5])
        assertEquals("Postkonto ••-••••••-9", out[6])
        assertEquals("Telefon [number], [email]", out[7])
        assertEquals(lines[8], out[8])
        assertEquals(lines[9], out[9])
    }

    @Test
    fun `the block keeps at most 40 lines and says how many there were`() {
        val block = StatementDiagnostics.block("PDF text", (1..100).map { "line $it" })
        assertTrue(block.startsWith("Diagnostics, redacted (PDF text, first 40 of 100 lines):"))
        assertEquals(41, block.lines().size)
        assertFalse(block.contains("line 41"))
    }
}
