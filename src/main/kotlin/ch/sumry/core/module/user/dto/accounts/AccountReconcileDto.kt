package ch.sumry.core.module.user.dto.accounts

/**
 * Bring an account in line with reality ("abgleichen").
 *
 * The new balance is what the real bank or wallet actually shows; the difference is
 * booked as an adjustment transaction rather than silently overwriting the balance, so
 * history keeps adding up to the current figure.
 */
data class AccountReconcileDto(
    val newBalance: Double,
    val note: String? = null,
)
