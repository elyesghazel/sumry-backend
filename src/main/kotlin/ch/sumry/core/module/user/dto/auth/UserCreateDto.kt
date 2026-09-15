package ch.sumry.core.module.user.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UserCreateDto(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:Email(message = "Invalid email format")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 12, message = "Password must be at least 12 characters long")
    val password: String
)
