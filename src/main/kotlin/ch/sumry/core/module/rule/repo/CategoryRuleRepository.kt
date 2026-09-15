package ch.sumry.core.module.rule.repo

import ch.sumry.core.module.rule.model.CategoryRule
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface CategoryRuleRepository : JpaRepository<CategoryRule, UUID> {
    fun findByUser_IdOrderByPriorityAscCreatedAtAsc(userId: UUID): List<CategoryRule>
    fun findByUser_IdAndIsActiveTrueOrderByPriorityAscCreatedAtAsc(userId: UUID): List<CategoryRule>
}
