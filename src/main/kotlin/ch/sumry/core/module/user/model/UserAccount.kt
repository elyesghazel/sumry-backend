package ch.sumry.core.module.user.model

import com.fasterxml.jackson.annotation.JsonIgnore
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


    // The owner is carried as a plain id on the response DTO; serialising the whole
    // User from here dragged the password hash into the payload.
    @field:JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    val user: User? = null,

    @Version // Ensures Hibernate tracks concurrent changes
    var version: Long? = null

) {
    constructor(accountName: String, currentBalance: Double, user: User) : this(
        null,
        accountName,
        currentBalance,
        user
    )
}