package ch.sumry.core.module.budget.dto

data class BudgetProgressDTO(
    val categoryLabel: String,
    val limitAmount: Double,
    val spentAmount: Double,
    val remaining: Double,
    val percentage: Double
)
