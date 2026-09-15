package ch.sumry.core.module.user.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.auth.*
import ch.sumry.core.module.user.mapper.UserMapper
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.security.JwtService
import ch.sumry.core.security.LoginRateLimiter
import ch.sumry.core.security.TokenClaims
import ch.sumry.core.security.TokenRevocationService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.security.Principal
import java.util.*

@Service
class UserService(
    private val repo: UserRepository,
    private val jwtService: JwtService,
    private val rateLimiter: LoginRateLimiter,
    private val revocation: TokenRevocationService,
    /**
     * Self-registration is off by default. The API is reachable from the internet, so an
     * open register endpoint lets anyone create accounts on a personal instance.
     */
    @Value("\${sumry.registration.enabled:false}")
    private val registrationEnabled: Boolean,
) {

    fun getAll(): List<UserResponseDto> = repo.findAll().map { UserMapper.toDto(it) }

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
        if (!registrationEnabled) {
            throw RequestException(HttpStatus.FORBIDDEN, "Registration is disabled on this instance")
        }
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
    fun changePassword(dto: PasswordChangeDto, principal: Principal): LoginResponseDto {
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
        // Every other session dies with the old password; the caller gets a token that fits
        // the new version so it does not have to sign in again.
        user.tokenVersion += 1
        repo.save(user)
        revocation.revokeAll(userId)
        return issueToken(user)
    }

    /** Revokes exactly the token this request was made with. */
    fun logout(claims: TokenClaims?) {
        if (claims != null) revocation.revoke(claims)
    }

    /** Revokes every token the user holds, on every device. */
    fun logoutEverywhere(principal: Principal) {
        revocation.revokeAll(UUID.fromString(principal.name))
    }

    fun login(dto: LoginDto, request: HttpServletRequest): LoginResponseDto {
        rateLimiter.retryAfterSeconds(request, dto.email)?.let { retryAfter ->
            throw RequestException(HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Try again in $retryAfter seconds.")
        }
        val user = repo.findByEmail(dto.email)
            ?: throw RequestException(HttpStatus.UNAUTHORIZED, "Invalid email or password")

        val passwordEncoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        if (!passwordEncoder.matches(dto.password, user.password)) {
            throw RequestException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }
        rateLimiter.reset(dto.email)
        return issueToken(user)
    }

    private fun issueToken(user: User): LoginResponseDto {
        val token = jwtService.generateToken(
            subject = user.id.toString(),
            tokenVersion = user.tokenVersion,
            extraClaims = mapOf("email" to user.email, "username" to user.username, "role" to user.role.name)
        )
        return LoginResponseDto(token = token, email = user.email)
    }
}
