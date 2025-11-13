package ch.sumry.core.module.user.dto

import ch.sumry.core.module.user.model.User

data class UserAccountCreateDto(
    val user: User,
    val accountName: String,
    val currentBalance: Double = 0.0,

)
