package ch.sumry.core.module.transaction.repo

import ch.sumry.core.module.transaction.model.Transaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface TransactionRepository: JpaRepository<Transaction, UUID> {

    fun findByUserAccount_User_Id(userId: UUID): List<Transaction>
    fun findByUserAccount_IdAndDateBetween(
        accountId: UUID,
        startDate: Date,
        endDate: Date
    ): List<Transaction>

    fun findByTransferGroupId(transferGroupId: UUID): List<Transaction>

    @Query("""
    SELECT SUM(t.amount)
    FROM Transaction t
    WHERE t.category.id = :categoryId
      AND t.userAccount.user.id = :userId
      AND t.date BETWEEN :startDate AND :endDate
""")
    fun getSpentAmountForCategory(
        userId: UUID,
        categoryId: UUID,
        startDate: Date,
        endDate: Date
    ): Double?
}
