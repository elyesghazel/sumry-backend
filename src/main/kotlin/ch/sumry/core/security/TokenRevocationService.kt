package ch.sumry.core.security

import ch.sumry.core.module.user.repo.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.*

/**
 * Two ways a token stops working before it expires.
 *
 * A single sign-out puts the token's `jti` on a Redis denylist for however long it had left.
 * "Sign out everywhere" and a password change bump the user's token version instead, which
 * every token issued so far fails to match. The version is cached briefly so the check does
 * not cost a database read per request.
 *
 * Redis being unreachable fails closed: a token that cannot be checked is not accepted.
 */
@Service
class TokenRevocationService(
    private val redis: StringRedisTemplate,
    private val userRepository: UserRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * The user's current role when the token may still be used, else null.
     *
     * The role comes from the database, never from the token: a token signed with a leaked
     * key must not be able to raise its own privileges.
     */
    fun activeRole(claims: TokenClaims): String? {
        return try {
            if (redis.hasKey(denyKey(claims.jti))) return null
            val state = currentState(UUID.fromString(claims.subject)) ?: return null
            if (state.version == claims.tokenVersion) state.role else null
        } catch (ex: Exception) {
            log.error("Token check failed, refusing the request: {}", ex.message)
            null
        }
    }

    fun isActive(claims: TokenClaims): Boolean = activeRole(claims) != null

    fun revoke(claims: TokenClaims) {
        val remaining = Duration.ofMillis(claims.expiresAt.time - System.currentTimeMillis())
        if (remaining.isNegative || remaining.isZero) return
        redis.opsForValue().set(denyKey(claims.jti), "1", remaining)
    }

    @Transactional
    fun revokeAll(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow()
        user.tokenVersion += 1
        userRepository.save(user)
        redis.delete(versionKey(userId))
    }

    private data class UserState(val version: Int, val role: String)

    private fun currentState(userId: UUID): UserState? {
        val cached = redis.opsForValue().get(versionKey(userId))?.split(":")
        if (cached != null && cached.size == 2) {
            cached[0].toIntOrNull()?.let { return UserState(it, cached[1]) }
        }
        val user = userRepository.findById(userId).orElse(null) ?: return null
        val state = UserState(user.tokenVersion, user.role.name)
        redis.opsForValue().set(versionKey(userId), "${state.version}:${state.role}", VERSION_TTL)
        return state
    }

    private fun denyKey(jti: String) = "auth:revoked:$jti"
    // New key name: entries cached by the old version-only format must not be read as roles.
    private fun versionKey(userId: UUID) = "auth:tvr:$userId"

    companion object {
        private val VERSION_TTL: Duration = Duration.ofMinutes(10)
    }
}
