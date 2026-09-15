package ch.sumry.core.module.budget.dto

import java.math.BigDecimal

import java.util.*

data class BudgetResponseDTO(
    val id: UUID,
    val limitAmount: BigDecimal,
    val categoryLabel: String,
    val categoryColor: String?
)