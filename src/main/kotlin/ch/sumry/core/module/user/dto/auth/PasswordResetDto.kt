package ch.sumry.core.module.user.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PasswordResetDto(
    @field:NotBlank(message = "Reset token is required")
    val token: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 12, message = "New password must be at least 12 characters long")
    val newPassword: String,
)
