package ch.sumry.core.module.budget.dto

import java.math.BigDecimal

data class BudgetUpdateDTO(
    var limitAmount: BigDecimal? = null
)
