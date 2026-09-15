package ch.sumry.core.module.budget.dto

import ch.sumry.core.types.BudgetType
import java.math.BigDecimal
import java.util.*

data class BudgetResponseDTO(
    val id: UUID,
    val limitAmount: BigDecimal,
    val categoryLabel: String,
    val categoryColor: String?,
    val period: BudgetType,
    val rollover: Boolean,
    val startDay: Int,
)
