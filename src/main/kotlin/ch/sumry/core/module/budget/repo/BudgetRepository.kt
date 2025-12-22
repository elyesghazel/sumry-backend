package ch.sumry.core.module.budget.repo

import ch.sumry.core.module.budget.model.Budget
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface BudgetRepository: JpaRepository<Budget, UUID> {
    fun findAllByUserId(userId: UUID): List<Budget>

    fun existsByCategory_Id(categoryId: UUID): Boolean

}