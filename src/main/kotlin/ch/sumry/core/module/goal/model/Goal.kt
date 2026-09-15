package ch.sumry.core.module.goal.model

import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.types.Money
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "goals")
class Goal(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(name = "target_amount", precision = 14, scale = 2, nullable = false)
    var targetAmount: BigDecimal = Money.ZERO,

    @Column(name = "target_date")
    var targetDate: LocalDate? = null,

    /** When set, progress is this account's balance and contributions are transfers into it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    var account: UserAccount? = null,

    /** Progress kept by the goal itself when no account is linked. */
    @Column(name = "saved_amount", precision = 14, scale = 2, nullable = false)
    var savedAmount: BigDecimal = Money.ZERO,

    var color: String? = null,
    var icon: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "achieved_at")
    var achievedAt: Date? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Date = Date(),
)
