package ch.sumry.core.model

import jakarta.persistence.*
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "planned_transactions")
class PlannedTransaction(
    @Id
    @GeneratedValue
    var id: UUID? = null,

    var name: String? = null,
    var amount: Double? = null,
    var description: String? = null,
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null, // null = indefinite

    @Enumerated(EnumType.STRING)
    var frequency: Frequency? = null, // e.g. DAILY, WEEKLY, MONTHLY, YEARLY

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
