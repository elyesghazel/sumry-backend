package ch.sumry.core.model

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    var username: String,
    var email: String,

    @Column(nullable = false)
    var password: String,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val accounts: MutableList<UserAccount> = mutableListOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var transactions: MutableList<Transaction> = mutableListOf()
) {
    fun encodePassword(rawPassword: String) {
        val encoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        this.password = encoder.encode(rawPassword)
    }

}
