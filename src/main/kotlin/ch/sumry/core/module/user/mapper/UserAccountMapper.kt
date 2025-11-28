package ch.sumry.core.module.user.mapper

import ch.sumry.core.module.user.dto.accounts.UserAccountCreateDto
import ch.sumry.core.module.user.dto.accounts.UserAccountResponseDto
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.model.UserAccount

object UserAccountMapper {
    fun toDto(entity: UserAccount): UserAccountResponseDto =
        UserAccountResponseDto(
            id = entity.id,
            userId = entity.user?.id,
            accountName = entity.accountName,
            currentBalance = entity.currentBalance
        )

    fun fromDtoToEntity(dto: UserAccountCreateDto, userEntity: User): UserAccount =
        UserAccount(
            accountName = dto.accountName,
            currentBalance = dto.currentBalance,
            user = userEntity
        )
}