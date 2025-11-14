package ch.sumry.core.module.user.repo

import ch.sumry.core.module.user.model.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserAccountRepository: JpaRepository<UserAccount, UUID> {

    fun existsByAccountName(accountName: String?): Boolean
    fun findByAccountNameAndId(accountName: String, id: UUID): Optional<UserAccount>

    fun findByUserId(userId: UUID): List<UserAccount>
}