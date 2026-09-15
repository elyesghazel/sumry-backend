package ch.sumry.core.module.transaction.dto

import ch.sumry.core.types.TransactionKind
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class QuickEntryRequest(
    val text: String,
    /** Book it when everything needed was understood; otherwise only parse. */
    val book: Boolean = false,
    /** Overrides the account the text implies (or the default). */
    val accountId: UUID? = null,
)

data class QuickEntryParsed(
    val amount: BigDecimal?,
    val description: String,
    val date: LocalDate,
    val kind: TransactionKind,
    val accountId: UUID?,
    val accountName: String?,
    val toAccountId: UUID?,
    val toAccountName: String?,
    val categoryLabel: String?,
    val categoryColor: String?,
    /** explicit | rule | null — where the category came from. */
    val categorySource: String?,
)

data class QuickEntryDTO(
    val parsed: QuickEntryParsed,
    val missing: List<String>,
    val ready: Boolean,
    val booked: TransactionResponseDTO?,
    val transferGroupId: UUID?,
)
