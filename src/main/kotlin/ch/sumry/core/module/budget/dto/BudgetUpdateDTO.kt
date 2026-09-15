package ch.sumry.core.module.budget.dto

import ch.sumry.core.types.BudgetType
import java.math.BigDecimal

data class BudgetUpdateDTO(
    var limitAmount: BigDecimal? = null,
    val period: BudgetType? = null,
    val rollover: Boolean? = null,
    val startDay: Int? = null,
)
