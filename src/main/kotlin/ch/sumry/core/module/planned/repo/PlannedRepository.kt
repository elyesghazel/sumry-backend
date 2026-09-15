package ch.sumry.core.module.planned.repo

import ch.sumry.core.module.transaction.model.PlannedTransaction
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface PlannedRepository : JpaRepository<PlannedTransaction, UUID> {
    fun findByUser_IdOrderByStartDateAsc(userId: UUID): List<PlannedTransaction>
    fun findByUser_IdAndIsActiveTrueOrderByStartDateAsc(userId: UUID): List<PlannedTransaction>
    fun findByIsActiveTrueAndAutoBookTrue(): List<PlannedTransaction>
}
