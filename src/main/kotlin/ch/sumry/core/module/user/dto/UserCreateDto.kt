package ch.sumry.core.module.user.dto

data class UserCreateDto (
    val username: String,
    val email: String,
    val password: String
)
