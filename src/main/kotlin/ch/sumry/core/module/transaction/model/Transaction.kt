package ch.sumry.core.module.transaction.model

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.privacy.maskAccounts
import ch.sumry.core.types.TransactionKind
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

@Entity
@Table(name = "transactions")
class Transaction (

    @Id
    @GeneratedValue
    val id: UUID? = null,

    @Column(precision = 14, scale = 2)
    var amount: BigDecimal? = null,
    var description: String? = null,

    @Column(precision = 14, scale = 2)
    var balanceBefore: BigDecimal? = null,
    @Column(precision = 14, scale = 2)
    var balanceAfter: BigDecimal? = null,

    // null for transfer legs, which are categorised by their kind instead
    @ManyToOne
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    var kind: TransactionKind? = null,

    /** Shared by the two legs of a transfer; null for ordinary transactions. */
    @Column(name = "transfer_group_id")
    var transferGroupId: UUID? = null,

    var date: Date? = Date(),

    @ManyToOne(fetch = FetchType.LAZY)
    var userAccount: UserAccount? = null,

    /** The account's currency at booking; [amount] and the balances are in it. */
    @Column(nullable = false, length = 3)
    var currency: String = "CHF",

    /** Units of the base currency per unit of [currency] when this was booked. */
    @Column(name = "fx_rate", precision = 14, scale = 6, nullable = false)
    var fxRate: BigDecimal = BigDecimal.ONE,

    /** [amount] in the user's base currency — what every report sums. */
    @Column(name = "base_amount", precision = 14, scale = 2, nullable = false)
    var baseAmount: BigDecimal = BigDecimal.ZERO,

    ) {
    constructor(
        amount: BigDecimal,
        description: String?,
        balanceBefore: BigDecimal?,
        balanceAfter: BigDecimal?,
        category: Category,
        date: Date,
        userAccount: UserAccount
    ) : this(
        null,
        amount,
        description,
        balanceBefore,
        balanceAfter,
        category,
        category.categoryType?.let { TransactionKind.of(it) },
        null,
        date,
        userAccount,
        userAccount.currency,
        BigDecimal.ONE,
        amount,
    )

    /** Every write path (quick entry, shortcut, import, edits) ends here, so masking here covers them all. */
    @PrePersist
    @PreUpdate
    fun maskAccountNumbers() {
        description = maskAccounts(description)
    }

    /**
     * The kind of this transaction, falling back to the category type for rows written before
     * `kind` existed.
     */
    fun effectiveKind(): TransactionKind =
        kind
            ?: category?.categoryType?.let { TransactionKind.of(it) }
            ?: TransactionKind.EXPENSE
}
