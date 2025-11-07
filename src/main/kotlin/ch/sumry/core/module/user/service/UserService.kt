package ch.sumry.core.module.user.service

import ch.sumry.core.module.user.dto.UserCreateDto
import ch.sumry.core.module.user.dto.UserResponseDto
import ch.sumry.core.module.user.mapper.UserMapper
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.stereotype.Service


@Service
class UserService(private val repo: UserRepository) {

    fun getAll(): List<User> = repo.findAll()
    fun create(dto: UserCreateDto): UserResponseDto {
        if (repo.existsByUsername(dto.username)) {
            throw IllegalArgumentException("Username already exists")
        }
        if (repo.existsByEmail(dto.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val user = User(
            username = dto.username,
            email = dto.email,
            password = dto.password
        )
        user.encodePassword(dto.password)
        repo.save(user)

        return UserMapper.toDto(user)
    }
}
