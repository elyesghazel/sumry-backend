package ch.sumry.core.module.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginDto(
    @field:NotBlank(message = "Email cannot be blank")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Password cannot be blank")
    val password: String,
)
