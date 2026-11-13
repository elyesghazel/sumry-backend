package ch.sumry.core.module.porting

import ch.sumry.core.exception.RequestException
import ch.sumry.core.types.TransactionKind
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class StatementReaderTest {

    private val mapper = jacksonObjectMapper()
    private val accountId = "00000000-0000-0000-0000-000000000001"

    private val csv = """
        Datum von:;2026-10-01
        Buchungsdatum;Avisierungstext;Gutschrift in CHF;Lastschrift in CHF;Valuta;Saldo in CHF
        2026-10-02;"KAUF/DIENSTLEISTUNG VOM 01.10.2026 KARTEN NR. XXXX1234 MIGROS";;-23.50;2026-10-02;1226.50
        2026-10-05;"GUTSCHRIFT VON CH5604835012345678009 ARBEITGEBER";3450.00;;2026-10-05;4676.50
    """.trimIndent()

    @Test
    fun `the old json shape with csv text still works`() {
        val json = mapper.writeValueAsString(mapOf("accountId" to accountId, "format" to "postfinance", "csv" to csv))
        val req: ImportPreviewRequest = mapper.readValue(json)
        assertNull(req.fileBase64)
        val r = StatementReader.read(req)
        assertEquals("postfinance", r.format)
        assertEquals(2, r.rows.size)
        assertEquals("MIGROS", r.rows[0].description)
        assertEquals(TransactionKind.INCOME, r.rows[1].kind)
        assertEquals(BigDecimal("4676.50"), r.rows[1].balanceAfter)
    }

    @Test
    fun `csv rows are IBAN-masked too`() {
        val r = StatementReader.read(ImportPreviewRequest(UUID.fromString(accountId), csv = csv))
        assertEquals("GUTSCHRIFT VON CH•• … 8009 ARBEITGEBER", r.rows[1].rawDescription)
        assertFalse(r.rows[1].description.contains("0483"))
    }

    @Test
    fun `a csv file sent as base64 in windows-1252 is read`() {
        val bytes = csv.replace("MIGROS", "MIGROS ZÜRICH").toByteArray(charset("windows-1252"))
        val json = """{"accountId":"$accountId","fileBase64":"${Base64.getEncoder().encodeToString(bytes)}","fileName":"export.csv"}"""
        val r = StatementReader.read(mapper.readValue<ImportPreviewRequest>(json))
        assertEquals("postfinance", r.format)
        assertEquals("MIGROS ZÜRICH", r.rows[0].description)
    }

    @Test
    fun `missing csv and file is a bad request`() {
        val req: ImportPreviewRequest = mapper.readValue("""{"accountId":"$accountId","csv":null}""")
        val ex = assertThrows<RequestException> { StatementReader.read(req) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun `files over 10 MB are refused before decoding`() {
        val big = "A".repeat(StatementReader.MAX_FILE_BYTES / 3 * 4 + 100)
        val ex = assertThrows<RequestException> {
            StatementReader.read(ImportPreviewRequest(UUID.fromString(accountId), fileBase64 = big))
        }
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.status)
    }

    @Test
    fun `invalid base64 is a bad request`() {
        val ex = assertThrows<RequestException> {
            StatementReader.read(ImportPreviewRequest(UUID.fromString(accountId), fileBase64 = "%%% not base64 %%%"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }
}
