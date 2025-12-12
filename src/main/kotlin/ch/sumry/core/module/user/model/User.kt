package ch.sumry.core.module.user.model

import com.fasterxml.jackson.annotation.JsonIgnore
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

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val accounts: MutableList<UserAccount> = mutableListOf(),

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL] )
    var preferences: UserPreferences? = null,

    @Enumerated(EnumType.STRING)
    var role: Role = Role.USER
) {

    constructor() : this(UUID.randomUUID(), "", "", "", mutableListOf(), null)
    fun encodePassword(rawPassword: String) {
        val encoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        this.password = encoder.encode(rawPassword)
    }

}
