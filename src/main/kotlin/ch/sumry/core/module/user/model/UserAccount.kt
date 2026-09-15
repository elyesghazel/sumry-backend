package ch.sumry.core.module.user.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*


@Entity
@Table(name = "user_accounts")
class UserAccount (
    @Id
    @GeneratedValue
    val id: UUID? = null,

    var accountName: String? = null,
    @Column(precision = 14, scale = 2)
    var currentBalance: BigDecimal? = null,

    /** ISO 4217. Balances and every transaction on this account are in this currency. */
    @Column(nullable = false, length = 3)
    var currency: String = "CHF",


    // The owner is carried as a plain id on the response DTO; serialising the whole
    // User from here dragged the password hash into the payload.
    @field:JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    val user: User? = null,

    @Version // Ensures Hibernate tracks concurrent changes
    var version: Long? = null

) {
    constructor(accountName: String, currentBalance: BigDecimal, user: User, currency: String = "CHF") : this(
        null,
        accountName,
        currentBalance,
        currency,
        user
    )
}