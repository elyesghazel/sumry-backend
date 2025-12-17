package ch.sumry.core.module.transaction.model

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.UserAccount
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

    // 🚨 FIX: Mache es nullable und setze den Default auf null
    @ManyToOne
    @JoinColumn(name = "category_id")
    var category: Category? = null,


    val date: Date? = Date(), // Ändere dies sicherheitshalber auch auf Date? = Date()

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
        date,
        userAccount
    )
}