package ch.sumry.core.module.user.model

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "user_preferences")
data class UserPreferences(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    val preferredCurrency: String = "USD",
    val theme: String = "dark",
    val language: String = "en"
) {
    constructor(user: User) : this(
        id = UUID.randomUUID(),
        user = user
    )
}
