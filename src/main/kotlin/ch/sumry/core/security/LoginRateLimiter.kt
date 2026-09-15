package ch.sumry.core.security

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Bounds online password guessing. Two counters per window: one per client address so a
 * single machine cannot hammer many accounts, one per email so one account cannot be
 * attacked from many addresses.
 *
 * Unlike token revocation this fails open — losing Redis should not lock everyone out of
 * signing in, it only loses the throttle until Redis is back.
 */
@Service
class LoginRateLimiter(private val redis: StringRedisTemplate) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Seconds until another attempt is allowed, or null when the attempt may proceed. */
    fun retryAfterSeconds(request: HttpServletRequest, email: String): Long? {
        return try {
            val ip = clientAddress(request)
            val ipHits = hit("auth:rl:ip:$ip")
            val emailHits = hit("auth:rl:email:${email.trim().lowercase()}")
            if (ipHits > IP_LIMIT || emailHits > EMAIL_LIMIT) {
                val ttl = maxOf(
                    redis.getExpire("auth:rl:ip:$ip") ?: 0L,
                    redis.getExpire("auth:rl:email:${email.trim().lowercase()}") ?: 0L,
                )
                maxOf(ttl, 1L)
            } else null
        } catch (ex: Exception) {
            log.warn("Login rate limiter unavailable, allowing the attempt: {}", ex.message)
            null
        }
    }

    /** A correct password clears the per-account counter; the address counter stays. */
    fun reset(email: String) {
        try {
            redis.delete("auth:rl:email:${email.trim().lowercase()}")
        } catch (ex: Exception) {
            log.warn("Could not reset login counter: {}", ex.message)
        }
    }

    private fun hit(key: String): Long {
        val count = redis.opsForValue().increment(key) ?: 1L
        if (count == 1L) redis.expire(key, WINDOW)
        return count
    }

    /**
     * Traefik is the only way into this container, so the first hop of X-Forwarded-For is
     * the real client; without the header (local dev) the socket address is.
     */
    private fun clientAddress(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr

    companion object {
        private val WINDOW: Duration = Duration.ofMinutes(15)
        private const val IP_LIMIT = 30L
        private const val EMAIL_LIMIT = 10L
    }
}
