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

    // Never serialise the hash. Belt and braces with the DTO-returning controllers:
    // an endpoint that returns this entity by mistake still cannot leak it.
    @field:JsonIgnore
    @Column(nullable = false)
    var password: String,

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val accounts: MutableList<UserAccount> = mutableListOf(),

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL] )
    var preferences: UserPreferences? = null,

    @Enumerated(EnumType.STRING)
    var role: Role = Role.USER,

    /**
     * Carried in every token as the `tv` claim and compared on each request. Incrementing
     * it is how "sign out everywhere" and a password change revoke tokens already issued.
     */
    @Column(name = "token_version", nullable = false)
    var tokenVersion: Int = 0,
) {

    constructor() : this(UUID.randomUUID(), "", "", "", mutableListOf(), null)
    fun encodePassword(rawPassword: String) {
        val encoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        this.password = encoder.encode(rawPassword)
    }

}
