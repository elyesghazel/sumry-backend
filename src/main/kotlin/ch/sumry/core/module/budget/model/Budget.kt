package ch.sumry.core.module.budget.model

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.User
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "user_budgets")
class Budget(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    var maxAmount: Double = 0.0,

    @ManyToOne
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @ManyToOne
    @JoinColumn(name = "user_id")
    var user: User? = null

) {
    constructor(maxAmount: Double, category: Category, user: User) : this(
        null,
        maxAmount,
        category,
        user
    )
}