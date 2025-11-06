package ch.sumry.core.dto

data class UserCreateDto (
    val username: String,
    val email: String,
    val password: String
)
