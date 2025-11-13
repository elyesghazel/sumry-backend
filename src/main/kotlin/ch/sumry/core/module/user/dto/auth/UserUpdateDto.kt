package ch.sumry.core.module.user.dto

import jakarta.validation.constraints.Email
import java.util.*

data class UserUpdateDto (

    val id: UUID,

    val username: String? = null,

    @field:Email(message = "Invalid email format")
    val email: String? = null,

)
