package ch.sumry.core.module.user.dto.accounts

import java.util.*

data class UserAccountResponseDto (
    val id: UUID?,
    val userId: UUID?,
    val accountName: String?,
    val currentBalance: Double?,
)
