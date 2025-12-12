package ch.sumry.core.module.transaction.dto

import ch.sumry.core.module.user.model.User
import java.util.*

data class TransactionResponseDTO(
    val id: UUID?,
    val amount: Double?,
    val description: String?,

    var categoryId: UUID, // Assumed non-nullable if transaction exists

    val date: Date?,
    val userAccountId: UUID?,
    val user: User?
)