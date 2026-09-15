package ch.sumry.core.module.budget.dto

import java.math.BigDecimal

data class BudgetProgressDTO(
    val categoryLabel: String,
    val limitAmount: BigDecimal,
    val spentAmount: BigDecimal,
    val remaining: BigDecimal,
    val percentage: Double
)
