package ch.sumry.core.module.budget.dto

import java.math.BigDecimal

data class BudgetCreateDTO (
    val maxAmount: BigDecimal,
    val categoryLabel: String
)