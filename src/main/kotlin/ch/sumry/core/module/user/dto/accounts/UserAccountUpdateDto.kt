package ch.sumry.core.module.user.dto.accounts

data class UserAccountUpdateDto(
    val accountName: String? = null,
    val currentBalance: Double? = null,
)
