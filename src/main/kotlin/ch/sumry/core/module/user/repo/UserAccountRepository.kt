package ch.sumry.core.module.user.repo

import ch.sumry.core.module.user.model.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserAccountRepository: JpaRepository<UserAccount, UUID> {

    fun existsByAccountNameAndUserId(accountName: String?, userId: UUID?): Boolean

    fun findByUserId(userId: UUID): List<UserAccount>

    fun countByUserId(userId: UUID): Long
}