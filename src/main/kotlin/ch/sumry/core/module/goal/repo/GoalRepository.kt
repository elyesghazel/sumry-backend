package ch.sumry.core.module.goal.repo

import ch.sumry.core.module.goal.model.Goal
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface GoalRepository : JpaRepository<Goal, UUID> {
    fun findByUser_IdOrderByCreatedAtAsc(userId: UUID): List<Goal>
    fun findByUser_IdAndIsActiveTrueOrderByCreatedAtAsc(userId: UUID): List<Goal>
}
