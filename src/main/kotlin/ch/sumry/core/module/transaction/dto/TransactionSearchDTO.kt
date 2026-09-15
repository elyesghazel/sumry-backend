package ch.sumry.core.module.transaction.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/** Every field optional; unset means "no constraint on this axis". */
data class TransactionSearchQuery(
    /** Case-insensitive substring over the note and the category label. */
    val q: String? = null,
    val accountId: UUID? = null,
    val categoryLabel: String? = null,
    /** INCOME, EXPENSE or TRANSFER — the latter matches both legs. */
    val kind: String? = null,
    val from: LocalDate? = null,
    /** Inclusive. */
    val to: LocalDate? = null,
    val min: BigDecimal? = null,
    val max: BigDecimal? = null,
    val page: Int = 0,
    val size: Int = 50,
    /** date | amount */
    val sort: String = "date",
    /** asc | desc */
    val dir: String = "desc",
)

data class TransactionSums(
    val income: BigDecimal,
    val expense: BigDecimal,
    val net: BigDecimal,
    val count: Long,
)

data class TransactionPageDTO(
    val items: List<TransactionResponseDTO>,
    val page: Int,
    val size: Int,
    val total: Long,
    val totalPages: Int,
    /** Totals over everything the filter matched, not just this page. */
    val sums: TransactionSums,
)
