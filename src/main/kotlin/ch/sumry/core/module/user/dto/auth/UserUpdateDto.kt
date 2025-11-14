package ch.sumry.core.module.user.dto.auth

import jakarta.validation.constraints.Email

data class UserUpdateDto (

    val username: String? = null,

    @field:Email(message = "Invalid email format")
    val email: String? = null,

)
