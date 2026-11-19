package ch.sumry.core.module.porting

import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.types.TransactionKind
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class ImportPreviewRequest(
    val accountId: UUID,
    /** auto | postfinance | revolut | generic | postfinance-pdf | camt053 */
    val format: String? = "auto",
    /** Pasted CSV text, the original shape. Optional when [fileBase64] is sent. */
    val csv: String? = "",
    /** An uploaded statement file (PDF, camt.053/052/054 XML or CSV), base64, at most 10 MB. */
    val fileBase64: String? = null,
    /** The file's name; only used as a hint when the content does not say what it is. */
    val fileName: String? = null,
)

data class ImportRowDTO(
    val line: Int,
    val date: LocalDate,
    val description: String,
    val rawDescription: String,
    val amount: BigDecimal,
    val kind: TransactionKind,
    val currency: String?,
    /** What the rules suggest, or null. */
    val categoryLabel: String?,
    val duplicate: Boolean,
    val duplicateOfId: UUID?,
    /** True when the statement currency disagrees with the account's. */
    val currencyMismatch: Boolean,
    /** The entries this row was matched to (one, or several for a split); empty when new. */
    val matchIds: List<UUID> = emptyList(),
    /** same-day | near | split, null when unmatched. */
    val matchKind: String? = null,
    /** What it matched, for the user: "Kebab · 9 Oct" or "Transfer 100 + Revolut fee 0.35 · 2 Oct". */
    val matchNote: String? = null,
    /** The bank's booking day when [date] is the earlier purchase day from the text. */
    val bookingDate: LocalDate? = null,
)

data class ImportPreviewDTO(
    val format: String,
    val accountId: UUID,
    val accountCurrency: String,
    val rows: List<ImportRowDTO>,
    val newCount: Int,
    val duplicateCount: Int,
    val uncategorisedCount: Int,
    val warnings: List<String>,
    /** The statement's closing balance when it had one; compare with the account after import. */
    val statementClosingBalance: BigDecimal?,
    /** The period the statement covers (stated in the file, else its first and last booking). */
    val statementFrom: LocalDate? = null,
    val statementTo: LocalDate? = null,
    val statementOpeningBalance: BigDecimal? = null,
    /** Opening balance plus every parsed row; equal to the closing balance when nothing was misread. */
    val computedClosingBalance: BigDecimal? = null,
    /** True when opening + rows = closing, false when they differ, null when the file has no balances. */
    val balanceOk: Boolean? = null,
    /** Whether POST /api/import/check (Claude) is configured on this server and allowed for this user. */
    val aiCheckAvailable: Boolean = false,
)

data class ImportCommitRow(
    val line: Int,
    val date: LocalDate,
    val description: String?,
    val amount: BigDecimal,
    val kind: TransactionKind,
    val categoryLabel: String?,
)

data class ImportCommitRequest(
    val accountId: UUID,
    val rows: List<ImportCommitRow>,
    /** Used for rows that arrive without a category. */
    val defaultExpenseCategory: String? = null,
    val defaultIncomeCategory: String? = null,
)

data class ImportSkipped(val line: Int, val reason: String)

data class ImportCommitDTO(
    val booked: Int,
    val skipped: List<ImportSkipped>,
    val transactions: List<TransactionResponseDTO>,
)

data class ImportCheckRow(
    val line: Int,
    val date: LocalDate,
    val bookingDate: LocalDate? = null,
    val description: String,
    val amount: BigDecimal,
    val kind: TransactionKind,
)

data class ImportCheckRequest(
    val accountId: UUID,
    /** The rows still unmatched after the preview, as shown (IBANs already masked). */
    val rows: List<ImportCheckRow>,
    /** Entries the preview already matched; they are not offered to Claude again. */
    val matchedIds: List<UUID> = emptyList(),
    val statementFrom: LocalDate? = null,
    val statementTo: LocalDate? = null,
)

data class ImportCheckSuggestion(
    val line: Int,
    /** logged | new | suspicious */
    val verdict: String,
    val matchIds: List<UUID> = emptyList(),
    val categoryLabel: String? = null,
    val reason: String,
)

data class ImportCheckAdjustment(val id: UUID, val note: String)

data class ImportCheckDTO(
    val suggestions: List<ImportCheckSuggestion>,
    /** Hand-made reconcile adjustments that the new rows would count a second time. */
    val adjustments: List<ImportCheckAdjustment>,
    val summary: String,
)
