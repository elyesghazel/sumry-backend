package ch.sumry.core.module.user.mapper

import ch.sumry.core.module.user.dto.auth.UserCreateDto
import ch.sumry.core.module.user.dto.auth.UserResponseDto
import ch.sumry.core.module.user.model.User

object UserMapper {

    fun toDto(entity: User): UserResponseDto =
        UserResponseDto(
            id = entity.id,
            username = entity.username,
            email = entity.email
        )

    fun fromRegisterDto(dto: UserCreateDto): User =
        User(
            username = dto.username!!,
            email = dto.email!!,
            password = dto.password
        )
}