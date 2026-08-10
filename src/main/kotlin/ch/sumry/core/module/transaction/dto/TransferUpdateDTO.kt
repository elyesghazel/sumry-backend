package ch.sumry.core.module.transaction.dto

import jakarta.validation.constraints.Positive
import java.util.*

data class TransferUpdateDTO(
    @field:Positive(message = "Amount must be greater than zero")
    val amount: Double? = null,

    val description: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val date: Date? = null,
)
