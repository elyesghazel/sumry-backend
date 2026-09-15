package ch.sumry.core.types

import java.math.BigDecimal

/**
 * What a transaction does to the balance of the account it belongs to.
 *
 * INCOME/EXPENSE are derived from the category type. TRANSFER_OUT/TRANSFER_IN are the two
 * legs of a transfer between two of the user's own accounts; they carry no category and are
 * excluded from income/expense reporting.
 */
enum class TransactionKind {
    INCOME,
    EXPENSE,
    TRANSFER_IN,
    TRANSFER_OUT;

    val isTransfer: Boolean
        get() = this == TRANSFER_IN || this == TRANSFER_OUT

    /** The signed effect a positive [amount] of this kind has on the account balance. */
    fun signedEffect(amount: BigDecimal): BigDecimal =
        if (this == INCOME || this == TRANSFER_IN) amount else amount.negate()

    companion object {
        fun of(categoryType: CategoryType): TransactionKind =
            if (categoryType == CategoryType.INCOME) INCOME else EXPENSE
    }
}
