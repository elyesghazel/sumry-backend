package ch.sumry.core.module.user.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.auth.*
import ch.sumry.core.module.user.mapper.UserMapper
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.security.JwtService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.security.Principal
import java.util.*

@Service
class UserService(private val repo: UserRepository, private val jwtService: JwtService) {

    fun getAll(): List<User> = repo.findAll()

    fun getById(id: UUID, principal: Principal?): UserResponseDto {

        val providedUserId = UUID.fromString(principal?.name)

        if (providedUserId != id) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }
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


    fun update(userId: String, dto: UserUpdateDto, principal: Principal): UserResponseDto {
        val providedUserId = principal.name

        if (providedUserId != userId) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }
        val user = repo.findById(UUID.fromString(userId)).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "User not found")
        }

        // Only overwrite if the field is non-null
        dto.username?.let { user.username = it }
        dto.email?.let { user.email = it }

        repo.save(user)
        return UserMapper.toDto(user)
    }

    /**
     * Changes the caller's own password.
     *
     * Knowing the current password is required even though the caller already holds a valid
     * token, so a leaked or borrowed token cannot be used to lock the real owner out.
     */
    fun changePassword(dto: PasswordChangeDto, principal: Principal) {
        val userId = UUID.fromString(principal.name)

        val user = repo.findById(userId).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "User not found")
        }

        val encoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        if (!encoder.matches(dto.currentPassword, user.password)) {
            throw RequestException(HttpStatus.UNAUTHORIZED, "Current password is incorrect")
        }
        if (encoder.matches(dto.newPassword, user.password)) {
            throw RequestException(HttpStatus.BAD_REQUEST, "New password must differ from the current one")
        }

        user.encodePassword(dto.newPassword)
        repo.save(user)
    }

    fun login(dto: LoginDto): LoginResponseDto {
        val user = repo.findByEmail(dto.email)
            ?: throw RequestException(HttpStatus.UNAUTHORIZED, "Invalid email or password")

        val passwordEncoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        if (!passwordEncoder.matches(dto.password, user.password)) {
            throw RequestException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }

        val token = jwtService.generateToken(
            subject = user.id.toString(),
            extraClaims = mapOf("email" to user.email, "username" to user.username, "role" to user.role.name)
        )

        return LoginResponseDto(
            token = token,
            email = user.email
        )
    }
}
