package ch.sumry.core.module.transaction.dto

import java.math.BigDecimal

import jakarta.validation.constraints.Positive
import java.util.*

data class TransferUpdateDTO(
    @field:Positive(message = "Amount must be greater than zero")
    val amount: BigDecimal? = null,

    val description: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val date: Date? = null,
    val toAmount: BigDecimal? = null,
)
