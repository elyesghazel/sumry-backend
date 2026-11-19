package ch.sumry.core.module.user.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.security.TokenRevocationService
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * One-time password reset tokens. An admin creates one and passes it on by hand for now;
 * delivery by mail comes later. Only a SHA-256 hash is stored, a new token replaces any
 * unused one of the same user, and a token works once within [LIFETIME].
 */
@Service
class PasswordResetService(
    private val jdbc: JdbcTemplate,
    private val users: UserRepository,
    private val revocation: TokenRevocationService,
) {
    private val random = SecureRandom()

    data class IssuedToken(val token: String, val expiresAt: Instant)

    @Transactional
    fun create(userId: UUID): IssuedToken {
        if (!users.existsById(userId)) throw RequestException(HttpStatus.NOT_FOUND, "User not found")
        val token = newToken()
        val now = Instant.now()
        val expiresAt = now.plus(LIFETIME)
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL", userId)
        jdbc.update(
            "INSERT INTO password_reset_tokens (id, token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), hash(token), userId, Timestamp.from(now), Timestamp.from(expiresAt),
        )
        return IssuedToken(token, expiresAt)
    }

    /**
     * Sets a new password and signs the user out everywhere. The token is claimed with a
     * conditional update, so two requests racing with the same token cannot both succeed.
     * A locked user can still reset; the lock keeps refusing the login.
     */
    @Transactional
    fun consume(token: String, newPassword: String) {
        val userId = jdbc.query(
            """
            UPDATE password_reset_tokens SET used_at = now()
            WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
            RETURNING user_id
            """,
            { rs, _ -> rs.getObject("user_id", UUID::class.java) },
            hash(token.trim()),
        ).firstOrNull() ?: throw RequestException(HttpStatus.BAD_REQUEST, "Reset token is invalid, used or expired")

        val user = users.findById(userId).orElseThrow { RequestException(HttpStatus.BAD_REQUEST, "Reset token is invalid, used or expired") }
        user.encodePassword(newPassword)
        users.save(user)
        revocation.revokeAll(userId)
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        val LIFETIME: Duration = Duration.ofHours(24)

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
