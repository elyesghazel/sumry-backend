package ch.sumry.core.module.user.dto.accounts

import java.util.*

data class UserAccountUpdateDto(
    val accountID: UUID?,
    val accountName: String?,
    val currentBalance: Double?,
)
