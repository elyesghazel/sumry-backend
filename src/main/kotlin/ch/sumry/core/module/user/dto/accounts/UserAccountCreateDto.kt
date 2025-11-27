package ch.sumry.core.module.user.dto.accounts

import java.util.*

data class UserAccountCreateDto(
    val userId: UUID,
    val accountName: String,
    val currentBalance: Double = 0.0,

)
