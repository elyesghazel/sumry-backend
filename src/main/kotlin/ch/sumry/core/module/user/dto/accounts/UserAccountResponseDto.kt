package ch.sumry.core.module.user.dto.accounts

import java.math.BigDecimal

import java.util.*

data class UserAccountResponseDto (
    val id: UUID?,
    val userId: UUID?,
    val accountName: String?,
    val currentBalance: BigDecimal?,
    val currency: String = "CHF",
    /** The balance in the user's base currency at today's rate. */
    val baseBalance: BigDecimal? = currentBalance,
    val baseCurrency: String = "CHF",
)
