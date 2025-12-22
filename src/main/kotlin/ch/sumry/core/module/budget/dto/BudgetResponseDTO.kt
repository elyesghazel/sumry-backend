package ch.sumry.core.module.budget.dto

import java.util.*

data class BudgetResponseDTO(
    val id: UUID,
    val limitAmount: Double,
    val categoryLabel: String,
    val categoryColor: String?
)