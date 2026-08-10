package ch.sumry.core.module.transaction.dto

import ch.sumry.core.types.TransactionKind
import java.util.*

data class TransactionResponseDTO(
    val id: UUID?,
    val amount: Double?,
    val description: String?,

    // null for transfer legs
    var categoryId: UUID?,
    val categoryLabel: String?,

    val kind: TransactionKind,
    val transferGroupId: UUID?,

    val date: Date?,
    val userAccountId: UUID?,
    val userId: UUID?,

    val balanceBefore: Double?,
    val balanceAfter: Double?
)
