package ch.sumry.core.module.user.dto.accounts

import java.math.BigDecimal
import java.time.Instant

import java.util.*

data class UserAccountResponseDto (
    val id: UUID?,
    val userId: UUID?,
    val accountName: String?,
    val currentBalance: BigDecimal?,
    val currency: String = "CHF",
    /** Counts toward safe-to-spend. */
    val spending: Boolean = true,
    /** The balance in the user's base currency at today's rate. */
    val baseBalance: BigDecimal? = currentBalance,
    val baseCurrency: String = "CHF",
    /** Last time the balance was confirmed via reconcile; null when never. */
    val lastReconciledAt: Instant? = null,
)
