package ch.sumry.core.module.transaction.mapper

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.transaction.model.Transaction
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.types.TransactionKind

object TransactionMapper {

    fun toDto(entity: Transaction): TransactionResponseDTO =
        TransactionResponseDTO(
            id = entity.id,
            amount = entity.amount,
            description = entity.description,
            categoryId = entity.category?.id,
            categoryLabel = entity.category?.label,
            kind = entity.effectiveKind(),
            transferGroupId = entity.transferGroupId,
            date = entity.date,
            userAccountId = entity.userAccount?.id,
            userId = entity.userAccount?.user?.id,
            balanceBefore = entity.balanceBefore,
            balanceAfter = entity.balanceAfter,
        )

    fun toDtoList(entities: List<Transaction>): List<TransactionResponseDTO> =
        entities.map { toDto(it) }


    fun fromDtoToEntity(
        dto: TransactionCreateDTO,
        categoryEntity: Category,
        userAccountEntity: UserAccount
    ): Transaction =
        Transaction(
            amount = dto.amount,
            description = dto.description,
            date = dto.date,
            category = categoryEntity,
            kind = categoryEntity.categoryType?.let { TransactionKind.of(it) } ?: TransactionKind.EXPENSE,
            userAccount = userAccountEntity,
        )

}
