package ch.sumry.core.module.user.dto


data class LoginResponseDto(
    val token: String,
    val email: String,
)
