package ch.sumry.core.module.user.dto.auth

import java.util.*

data class UserResponseDto(
    val id: UUID?,
    val username: String,
    val email: String,
)
