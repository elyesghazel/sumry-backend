package ch.sumry.core.module.user.model

import jakarta.persistence.*
import java.util.*


@Entity
@Table(name = "user_accounts")
class UserAccount (
    @Id
    @GeneratedValue
    val id: UUID? = null,

    var accountName: String? = null,
    var currentBalance: Double? = null,


    @ManyToOne(fetch = FetchType.LAZY)
    val user: User? = null,

) {
    constructor(accountName: String, currentBalance: Double, user: User) : this(
        UUID.randomUUID(),
        accountName,
        currentBalance,
        user
    )
}