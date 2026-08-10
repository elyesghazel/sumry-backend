package ch.sumry.core.module.user.repo

import ch.sumry.core.module.user.model.UserPreferences
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserPreferencesRepository : JpaRepository<UserPreferences, UUID> {
    fun findByUser_Id(userId: UUID): Optional<UserPreferences>
}
