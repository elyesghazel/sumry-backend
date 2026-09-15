package ch.sumry.core.module.transaction.dto

import ch.sumry.core.types.Money
import java.math.BigDecimal

data class CashflowDTO (
    val income: BigDecimal = Money.ZERO,
    val expenses: BigDecimal = Money.ZERO,
    )