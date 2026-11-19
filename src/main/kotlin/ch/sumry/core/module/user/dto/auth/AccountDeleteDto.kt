package ch.sumry.core.module.user.dto.auth

import jakarta.validation.constraints.NotBlank

data class AccountDeleteDto(
    @field:NotBlank(message = "Password is required")
    val password: String,
)
