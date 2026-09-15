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

    /** True when the token may still be used. */
    fun isActive(claims: TokenClaims): Boolean {
        return try {
            if (redis.hasKey(denyKey(claims.jti))) return false
            currentVersion(UUID.fromString(claims.subject)) == claims.tokenVersion
        } catch (ex: Exception) {
            log.error("Token check failed, refusing the request: {}", ex.message)
            false
        }
    }

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

    private fun currentVersion(userId: UUID): Int? {
        val cached = redis.opsForValue().get(versionKey(userId))
        if (cached != null) return cached.toIntOrNull()
        val version = userRepository.findById(userId).map { it.tokenVersion }.orElse(null) ?: return null
        redis.opsForValue().set(versionKey(userId), version.toString(), VERSION_TTL)
        return version
    }

    private fun denyKey(jti: String) = "auth:revoked:$jti"
    private fun versionKey(userId: UUID) = "auth:tv:$userId"

    companion object {
        private val VERSION_TTL: Duration = Duration.ofMinutes(10)
    }
}
