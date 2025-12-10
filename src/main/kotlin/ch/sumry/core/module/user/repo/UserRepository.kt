package ch.sumry.core.module.user.repo

import ch.sumry.core.module.user.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserRepository : JpaRepository<User, UUID> {

    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): User?
    fun findById(id: UUID?): Optional<User>
}
