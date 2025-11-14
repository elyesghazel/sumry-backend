package ch.sumry.core.module.user.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.auth.*
import ch.sumry.core.module.user.mapper.UserMapper
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.security.JwtService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserService(private val repo: UserRepository, private val jwtService: JwtService) {

    fun getAll(): List<User> = repo.findAll()

    fun getById(id: UUID): UserResponseDto {
        val user = repo.findById(id).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "User not found")
        }
        return UserMapper.toDto(user)
    }
    fun register(dto: UserCreateDto): UserResponseDto {
        if (repo.existsByUsername(dto.username)) {
            throw RequestException(HttpStatus.CONFLICT, "Username already taken")
        }
        if (repo.existsByEmail(dto.email)) {
            throw RequestException(HttpStatus.CONFLICT, "Email already exists")
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

    fun update(userId: String, dto: UserUpdateDto): UserResponseDto {
        val user = repo.findById(UUID.fromString(userId)).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "User not found")
        }

        // Only overwrite if the field is non-null
        dto.username?.let { user.username = it }
        dto.email?.let { user.email = it }

        repo.save(user)
        return UserMapper.toDto(user)
    }

    fun login(dto: LoginDto): LoginResponseDto {
        // 1. Find the user by email
        val user = repo.findByEmail(dto.email)

        if (user == null) {
            throw RequestException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }

        // 2. Check password
        val passwordEncoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        if (!passwordEncoder.matches(dto.password, user.password)) {
            throw RequestException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }

        // 3. Generate token
        val token = jwtService.generateToken(
            subject = user.email,
            extraClaims = mapOf("userId" to user.id.toString(), "username" to user.username)
        )

        // 4. Return DTO
        return LoginResponseDto(
            token = token,
            email = user.email
        )
    }




}
