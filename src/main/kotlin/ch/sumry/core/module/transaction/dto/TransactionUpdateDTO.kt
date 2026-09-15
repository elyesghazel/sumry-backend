package ch.sumry.core.module.transaction.dto

import java.math.BigDecimal

import java.util.*

data class TransactionUpdateDTO(
    val amount: BigDecimal? = null,
    val description: String? = null,
    val categoryLabel: String? = null,
    val date: Date? = null,
    val userAccountId: String? = null,
)
