package ch.sumry.core.module.budget.model

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.User
import ch.sumry.core.types.BudgetType
import jakarta.persistence.*
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "user_budgets")
class Budget (

    @Id
    @GeneratedValue
    val id: UUID? = null,

    var limitAmount: Double? = null,
    var label: String? = null,

    @Enumerated(EnumType.STRING)
    var budgetType: BudgetType? = null,

    var startDate: LocalDate? = null,


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: Category? = null
)