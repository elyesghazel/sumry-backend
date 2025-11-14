package ch.sumry.core.module.user.dto.auth


data class LoginResponseDto(
    val token: String,
    val email: String,
)
