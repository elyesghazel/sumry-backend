package ch.sumry.core.module.transaction.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.util.*

data class TransferCreateDTO(
    @field:Positive(message = "Amount must be greater than zero")
    val amount: Double,

    val description: String? = null,

    @field:NotBlank(message = "Source account is required")
    val fromAccountId: String,

    @field:NotBlank(message = "Destination account is required")
    val toAccountId: String,

    val date: Date,
)
