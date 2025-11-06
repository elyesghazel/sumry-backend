package ch.sumry.core.model

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "transactions")
class Transaction (

    @Id
    @GeneratedValue
    val id: UUID? = null,

    var amount: Double? = null,
    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    val userAccount: UserAccount? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null


) {
    constructor(amount: Double, description: String, userAccount: UserAccount) : this(
        UUID.randomUUID(),
        amount,
        description,
        userAccount
    )
}