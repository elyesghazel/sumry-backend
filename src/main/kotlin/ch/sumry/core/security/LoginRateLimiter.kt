package ch.sumry.core.security

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Bounds online password guessing and mass signups. Two counters per window: one per client address so a
 * single machine cannot hammer many accounts, one per email so one account cannot be
 * attacked from many addresses.
 *
 * Unlike token revocation this fails open — losing Redis should not lock everyone out of
 * signing in, it only loses the throttle until Redis is back.
 */
@Service
class LoginRateLimiter(
    private val redis: StringRedisTemplate,
    private val clientIp: ClientIpResolver,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Seconds until another attempt is allowed, or null when the attempt may proceed. Only
     * reads the counters: an attempt counts once [recordFailure] reports it as wrong, so
     * many correct sign-ins behind one shared address never lock it.
     */
    fun retryAfterSeconds(request: HttpServletRequest, email: String): Long? {
        return try {
            val ipKey = ipKey(request)
            val emailKey = emailKey(email)
            if (count(ipKey) >= IP_LIMIT || count(emailKey) >= EMAIL_LIMIT) {
                val ttl = maxOf(redis.getExpire(ipKey) ?: 0L, redis.getExpire(emailKey) ?: 0L)
                maxOf(ttl, 1L)
            } else null
        } catch (ex: Exception) {
            log.warn("Login rate limiter unavailable, allowing the attempt: {}", ex.message)
            null
        }
    }

    /** A failed sign-in counts against both the client address and the email. */
    fun recordFailure(request: HttpServletRequest, email: String) {
        try {
            hit(ipKey(request))
            hit(emailKey(email))
        } catch (ex: Exception) {
            log.warn("Could not record failed sign-in: {}", ex.message)
        }
    }

    /**
     * Signups are counted per client address only: there is no account yet to count
     * against. The window is longer because nobody signs up many times an hour.
     */
    fun signupRetryAfterSeconds(request: HttpServletRequest): Long? {
        return try {
            val key = "auth:rl:signup:ip:${clientIp.resolve(request)}"
            if (hit(key, SIGNUP_WINDOW) > SIGNUP_IP_LIMIT) maxOf(redis.getExpire(key) ?: 0L, 1L) else null
        } catch (ex: Exception) {
            log.warn("Signup rate limiter unavailable, allowing the attempt: {}", ex.message)
            null
        }
    }

    /** A correct password clears the per-account counter; the address counter stays. */
    fun reset(email: String) {
        try {
            redis.delete(emailKey(email))
        } catch (ex: Exception) {
            log.warn("Could not reset login counter: {}", ex.message)
        }
    }

    private fun ipKey(request: HttpServletRequest) = "auth:rl:ip:${clientIp.resolve(request)}"

    private fun emailKey(email: String) = "auth:rl:email:${email.trim().lowercase()}"

    private fun count(key: String): Long = redis.opsForValue().get(key)?.toLongOrNull() ?: 0L

    private fun hit(key: String, window: Duration = WINDOW): Long {
        val count = redis.opsForValue().increment(key) ?: 1L
        if (count == 1L) redis.expire(key, window)
        return count
    }

    companion object {
        private val WINDOW: Duration = Duration.ofMinutes(15)
        private const val IP_LIMIT = 30L
        private const val EMAIL_LIMIT = 10L
        private val SIGNUP_WINDOW: Duration = Duration.ofHours(1)
        private const val SIGNUP_IP_LIMIT = 5L
    }
}
