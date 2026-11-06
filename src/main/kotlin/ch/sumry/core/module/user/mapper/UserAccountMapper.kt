package ch.sumry.core.module.user.mapper

import ch.sumry.core.module.user.dto.accounts.UserAccountCreateDto
import ch.sumry.core.module.user.dto.accounts.UserAccountResponseDto
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.types.money
import java.math.BigDecimal

object UserAccountMapper {
    fun toDto(entity: UserAccount, baseCurrency: String = entity.currency, baseBalance: BigDecimal? = entity.currentBalance): UserAccountResponseDto =
        UserAccountResponseDto(
            id = entity.id,
            userId = entity.user?.id,
            accountName = entity.accountName,
            currentBalance = entity.currentBalance,
            currency = entity.currency,
            spending = entity.spending,
            baseBalance = baseBalance,
            baseCurrency = baseCurrency,
        )

    fun fromDtoToEntity(dto: UserAccountCreateDto, userEntity: User): UserAccount =
        UserAccount(
            accountName = dto.accountName,
            currentBalance = dto.currentBalance.money(),
            user = userEntity,
            currency = dto.currency.trim().uppercase().ifEmpty { "CHF" },
            spending = dto.spending,
        )

    fun toDtoList(entities: List<UserAccount>): List<UserAccountResponseDto> =
        entities.map { toDto(it) }
}