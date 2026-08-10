package ch.sumry.core.module.transaction.model

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.types.TransactionKind
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "transactions")
class Transaction (

    @Id
    @GeneratedValue
    val id: UUID? = null,

    var amount: Double? = null,
    var description: String? = null,

    var balanceBefore: Double? = null,
    var balanceAfter: Double? = null,

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

    ) {
    constructor(
        amount: Double,
        description: String?,
        balanceBefore: Double?,
        balanceAfter: Double?,
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
        userAccount
    )

    /**
     * The kind of this transaction, falling back to the category type for rows written before
     * `kind` existed.
     */
    fun effectiveKind(): TransactionKind =
        kind
            ?: category?.categoryType?.let { TransactionKind.of(it) }
            ?: TransactionKind.EXPENSE
}
