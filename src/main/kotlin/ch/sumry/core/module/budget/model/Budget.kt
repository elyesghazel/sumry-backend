package ch.sumry.core.module.budget.model

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.User
import ch.sumry.core.types.BudgetType
import ch.sumry.core.types.Money
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

@Entity
@Table(name = "user_budgets")
class Budget(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    @Column(precision = 14, scale = 2, nullable = false)
    var maxAmount: BigDecimal = Money.ZERO,

    @ManyToOne
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @ManyToOne
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var period: BudgetType = BudgetType.MONTHLY,

    /** Carry what was not spent (or overspent) into the next period. */
    @Column(nullable = false)
    var rollover: Boolean = false,

    /** Day a monthly period starts on, 1–28. Ignored for other periods. */
    @Column(name = "start_day", nullable = false)
    var startDay: Int = 1,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Date = Date(),
) {
    constructor(maxAmount: BigDecimal, category: Category, user: User) : this(
        null,
        maxAmount,
        category,
        user
    )
}
