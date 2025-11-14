package ch.sumry.core.module.transaction.model

import ch.sumry.core.module.user.model.User
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

    val date: Date = Date(),

    @ManyToOne(fetch = FetchType.LAZY)
    val userAccount: UserAccount? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null


) {
    constructor(amount: Double, description: String, date: Date, userAccount: UserAccount) : this(
        UUID.randomUUID(),
        amount,
    description,
        date,
        userAccount
    )
}