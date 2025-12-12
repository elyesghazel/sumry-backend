package ch.sumry.core.module.transaction.repo

import ch.sumry.core.module.transaction.model.Transaction
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface TransactionRepository: JpaRepository<Transaction, UUID> {

    fun findByUserAccount_User_Id(userId: UUID): List<Transaction>
}
