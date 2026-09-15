package ch.sumry.core.module.transaction.dto

import java.math.BigDecimal

import java.util.*

data class TransferResponseDTO(
    val transferGroupId: UUID,
    val amount: BigDecimal,
    val description: String?,
    val date: Date?,
    val fromAccountId: UUID?,
    val toAccountId: UUID?,
)
