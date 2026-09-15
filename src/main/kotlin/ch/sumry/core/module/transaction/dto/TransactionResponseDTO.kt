package ch.sumry.core.module.transaction.dto

import java.math.BigDecimal

import ch.sumry.core.types.TransactionKind
import java.util.*

data class TransactionResponseDTO(
    val id: UUID?,
    val amount: BigDecimal?,
    val description: String?,

    // null for transfer legs
    var categoryId: UUID?,
    val categoryLabel: String?,

    val kind: TransactionKind,
    val transferGroupId: UUID?,

    val date: Date?,
    val userAccountId: UUID?,
    val userId: UUID?,

    val balanceBefore: BigDecimal?,
    val balanceAfter: BigDecimal?,
    val currency: String = "CHF",
    val fxRate: BigDecimal = BigDecimal.ONE,
    val baseAmount: BigDecimal? = amount,
)
