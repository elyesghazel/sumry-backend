package ch.sumry.core.module.transaction.dto

import java.util.*

data class TransactionUpdateDTO(
    val amount: Double? = null,
    val description: String? = null,
    val categoryLabel: String? = null,
    val date: Date? = null,
    val userAccountId: String? = null,
)
