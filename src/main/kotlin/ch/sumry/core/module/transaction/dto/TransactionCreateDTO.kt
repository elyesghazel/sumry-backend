package ch.sumry.core.module.transaction.dto

import jakarta.validation.constraints.PositiveOrZero
import java.util.*

data class TransactionCreateDTO(
    @field:PositiveOrZero(message = "Amount must be positive")
    val amount: Double,

    val description: String?,

    val categoryLabel: String,

    val date: Date,

    val userAccountId: String,

)
