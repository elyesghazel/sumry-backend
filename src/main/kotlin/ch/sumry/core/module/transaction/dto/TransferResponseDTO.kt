package ch.sumry.core.module.transaction.dto

import java.util.*

data class TransferResponseDTO(
    val transferGroupId: UUID,
    val amount: Double,
    val description: String?,
    val date: Date?,
    val fromAccountId: UUID?,
    val toAccountId: UUID?,
)
