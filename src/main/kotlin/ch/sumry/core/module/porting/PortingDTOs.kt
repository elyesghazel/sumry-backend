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
