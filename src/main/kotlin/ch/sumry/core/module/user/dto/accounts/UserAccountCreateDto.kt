package ch.sumry.core.module.user.dto.accounts

import jakarta.validation.constraints.NotBlank

data class UserAccountCreateDto(
    @field:NotBlank(message = "Account name cannot be blank")
    val accountName: String,
    val currentBalance: Double = 0.0,

)
