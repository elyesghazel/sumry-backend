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

    @ManyToOne
    @JoinColumn(name = "category_id")
    var category: Category,


    val date: Date = Date(),

    @ManyToOne(fetch = FetchType.LAZY)
    var userAccount: UserAccount? = null,




) {
    constructor(amount: Double, description: String?, category: Category, date: Date, userAccount: UserAccount) : this(
        null,
        amount,
    description,
            category,
        date,
        userAccount
    )
}