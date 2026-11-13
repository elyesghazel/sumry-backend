package ch.sumry.core.module.porting

import ch.sumry.core.exception.RequestException
import ch.sumry.core.types.TransactionKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CamtParserTest {

    private val account = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun read(xml: String) = StatementReader.read(
        ImportPreviewRequest(account, "auto", fileBase64 = Base64.getEncoder().encodeToString(xml.toByteArray()), fileName = "auszug.xml"),
    )

    private val camt053 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08">
          <BkToCstmrStmt>
            <GrpHdr><MsgId>MSG-1</MsgId><CreDtTm>2026-11-01T06:00:00</CreDtTm></GrpHdr>
            <Stmt>
              <Id>STMT-1</Id>
              <FrToDt><FrDtTm>2026-10-01T00:00:00</FrDtTm><ToDtTm>2026-10-31T23:59:59</ToDtTm></FrToDt>
              <Acct><Id><IBAN>CH9300762011623852957</IBAN></Id><Ccy>CHF</Ccy></Acct>
              <Bal>
                <Tp><CdOrPrtry><Cd>OPBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="CHF">1250.00</Amt><CdtDbtInd>CRDT</CdtDbtInd><Dt><Dt>2026-09-30</Dt></Dt>
              </Bal>
              <Bal>
                <Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="CHF">4664.50</Amt><CdtDbtInd>CRDT</CdtDbtInd><Dt><Dt>2026-10-31</Dt></Dt>
              </Bal>
              <Ntry>
                <Amt Ccy="CHF">3450.00</Amt><CdtDbtInd>CRDT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts>
                <BookgDt><Dt>2026-10-05</Dt></BookgDt><ValDt><Dt>2026-10-05</Dt></ValDt>
                <AddtlNtryInf>GUTSCHRIFT VON CH56 0483 5012 3456 7800 9</AddtlNtryInf>
                <NtryDtls><TxDtls>
                  <RltdPties>
                    <Dbtr><Pty><Nm>Arbeitgeber AG</Nm></Pty></Dbtr>
                    <DbtrAcct><Id><IBAN>CH5604835012345678009</IBAN></Id></DbtrAcct>
                  </RltdPties>
                  <RmtInf><Ustrd>Lohn Oktober</Ustrd></RmtInf>
                </TxDtls></NtryDtls>
              </Ntry>
              <Ntry>
                <Amt Ccy="CHF">23.50</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts>
                <BookgDt><Dt>2026-10-02</Dt></BookgDt>
                <AddtlNtryInf>KAUF/DIENSTLEISTUNG VOM 01.10.2026 KARTEN NR. XXXX1234 MIGROS ZÜRICH</AddtlNtryInf>
              </Ntry>
              <Ntry>
                <Amt Ccy="CHF">12.00</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts>
                <BookgDt><Dt>2026-10-07</Dt></BookgDt>
                <NtryDtls><TxDtls>
                  <RltdPties><Cdtr><Nm>Bäckerei Müller</Nm></Cdtr></RltdPties>
                  <RmtInf><Ustrd>Rechnung 4711, Konto 12-345678-9</Ustrd></RmtInf>
                </TxDtls></NtryDtls>
              </Ntry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
    """.trimIndent()

    @Test
    fun `reads bookings, balances and the period of a camt 053`() {
        val r = read(camt053)
        assertEquals("camt053", r.format)
        assertEquals(3, r.rows.size)
        assertEquals(BigDecimal("1250.00"), r.openingBalance)
        assertEquals(BigDecimal("4664.50"), r.closingBalance)
        assertEquals(LocalDate.of(2026, 10, 1), r.statementFrom)
        assertEquals(LocalDate.of(2026, 10, 31), r.statementTo)

        val salary = r.rows[0]
        assertEquals(TransactionKind.INCOME, salary.kind)
        assertEquals(BigDecimal("3450.00"), salary.amount)
        assertEquals(LocalDate.of(2026, 10, 5), salary.date)
        assertEquals("Arbeitgeber AG · Lohn Oktober", salary.description)
        assertEquals("CHF", salary.currency)

        val card = r.rows[1]
        assertEquals(TransactionKind.EXPENSE, card.kind)
        assertEquals("MIGROS ZÜRICH", card.description)

        assertEquals("Bäckerei Müller · Rechnung 4711, Konto ••-••••••-9", r.rows[2].description)
        assertTrue(r.warnings.isEmpty(), r.warnings.toString())
    }

    @Test
    fun `never returns a full IBAN`() {
        val r = read(camt053)
        val all = r.rows.joinToString { it.description + " " + it.rawDescription }
        assertFalse(all.contains("0483 5012"), all)
        assertTrue(all.contains("CH•• … 8009"), all)
    }

    @Test
    fun `skips pending entries of a camt 052 and has no balances on a camt 054`() {
        val camt052 = """
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.08"><BkToCstmrAcctRpt><Rpt>
              <Ntry><Amt Ccy="CHF">5.00</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>PDNG</Cd></Sts><BookgDt><Dt>2026-10-02</Dt></BookgDt></Ntry>
              <Ntry><Amt Ccy="CHF">7.00</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts>BOOK</Sts><BookgDt><Dt>2026-10-02</Dt></BookgDt><AddtlNtryInf>Kiosk</AddtlNtryInf></Ntry>
            </Rpt></BkToCstmrAcctRpt></Document>
        """.trimIndent()
        val r = read(camt052)
        assertEquals("camt052", r.format)
        assertEquals(1, r.rows.size)
        assertTrue(r.warnings.any { it.contains("pending") })

        val camt054 = camt052.replace("camt.052", "camt.054").replace("BkToCstmrAcctRpt", "BkToCstmrDbtCdtNtfctn").replace("Rpt>", "Ntfctn>")
            .replace("<Sts><Cd>PDNG</Cd></Sts>", "")
        val n = read(camt054)
        assertEquals("camt054", n.format)
        assertEquals(2, n.rows.size)
        assertEquals(null, n.closingBalance)
    }

    @Test
    fun `refuses a DOCTYPE so external entities cannot be read`() {
        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE Document [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08"><BkToCstmrStmt><Stmt>
              <Ntry><Amt Ccy="CHF">1.00</Amt><CdtDbtInd>DBIT</CdtDbtInd><BookgDt><Dt>2026-10-02</Dt></BookgDt><AddtlNtryInf>&x;</AddtlNtryInf></Ntry>
            </Stmt></BkToCstmrStmt></Document>
        """.trimIndent()
        val ex = assertThrows<RequestException> { read(xxe) }
        assertFalse(ex.message.contains("root:"))
    }

    @Test
    fun `an empty statement answers with redacted diagnostics`() {
        val empty = """
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08"><BkToCstmrStmt><Stmt>
              <Acct><Id><IBAN>CH9300762011623852957</IBAN></Id><Ownr><Nm>Max Muster</Nm></Ownr></Acct>
            </Stmt></BkToCstmrStmt></Document>
        """.trimIndent()
        val r = read(empty)
        assertTrue(r.rows.isEmpty())
        val diag = r.warnings.single { it.startsWith("Diagnostics") }
        assertFalse(diag.contains("Max Muster"), diag)
        assertFalse(diag.contains("CH9300762011623852957"), diag)
        assertTrue(diag.contains("<Nm>…</Nm>"), diag)
    }

    @Test
    fun `camt pasted into the csv field is detected too`() {
        val r = StatementReader.read(ImportPreviewRequest(account, csv = camt053))
        assertEquals("camt053", r.format)
        assertEquals(3, r.rows.size)
    }
}
