package ch.sumry.core.module.transaction.model

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.model.UserAccount
import jakarta.persistence.*
import java.time.LocalDate
import java.math.BigDecimal
import java.util.*

@Entity
@Table(name = "planned_transactions")
class PlannedTransaction(
    @Id
    @GeneratedValue
    var id: UUID? = null,

    var name: String? = null,
    @Column(precision = 14, scale = 2)
    var amount: BigDecimal? = null,
    var description: String? = null,
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null, // null = indefinite

    @Enumerated(EnumType.STRING)
    var frequency: Frequency? = null, // e.g. DAILY, WEEKLY, MONTHLY, YEARLY

    @ManyToOne
    @JoinColumn(name = "category_id")
    var category: Category? = null,


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id")
    var userAccount: UserAccount? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null
)

enum class Frequency {
    DAILY, WEEKLY, MONTHLY, YEARLY, ONCE
}
