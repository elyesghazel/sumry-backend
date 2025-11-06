package ch.sumry.core.dto

import java.util.*

data class UserResponseDto(
    val id: UUID,
    val username: String,
    val email: String,
)
