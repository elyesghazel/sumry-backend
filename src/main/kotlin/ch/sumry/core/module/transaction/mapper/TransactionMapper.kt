package ch.sumry.core.module.transaction.mapper

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.transaction.model.Transaction
import ch.sumry.core.module.user.model.UserAccount

object TransactionMapper {

    fun toDto(entity: Transaction): TransactionResponseDTO =
        TransactionResponseDTO(
            id = entity.id,
            amount = entity.amount,
            description = entity.description,
            categoryId = entity.category.id!!, // Assuming Category ID is guaranteed to exist
            date = entity.date,
            userAccountId = entity.userAccount?.id, // Accessing UUID
            user = entity.userAccount?.user // Safe access
        )

    fun toDtoList(entities: List<Transaction>): List<TransactionResponseDTO> =
        entities.map { toDto(it) }


    fun fromDtoToEntity(
        dto: TransactionCreateDTO,
        categoryEntity: Category,
        userAccountEntity: UserAccount
    ): Transaction =
        Transaction(
            // DTO fields are non-nullable primitives/objects, so we can access them directly
            amount = dto.amount,
            description = dto.description,
            date = dto.date,
            category = categoryEntity,
            userAccount = userAccountEntity,
        )

}