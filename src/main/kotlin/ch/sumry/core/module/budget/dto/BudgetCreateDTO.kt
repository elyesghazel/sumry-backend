package ch.sumry.core.module.budget.dto

import ch.sumry.core.types.BudgetType
import java.math.BigDecimal

data class BudgetCreateDTO (
    val maxAmount: BigDecimal,
    val categoryLabel: String,
    val period: BudgetType = BudgetType.MONTHLY,
    val rollover: Boolean = false,
    val startDay: Int = 1,
)
