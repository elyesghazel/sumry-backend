package ch.sumry.core.module.statement

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.model.Role
import ch.sumry.core.module.user.model.User
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*

/**
 * Who a manually sent statement may go to, and how often.
 *
 * The statement goes out through the instance's own mail server, so a free choice of
 * recipient would let any account send mail anywhere. Users can only mail themselves;
 * admins may pass another address for testing. Manual sends are capped per user per day.
 *
 * Unlike the login limiter this fails closed: without Redis there is no count, and an
 * unbounded mail sender is worse than a statement that has to wait.
 */
@Component
class StatementMailPolicy(private val redis: StringRedisTemplate) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** The address the statement goes to, or 400 when [to] is not allowed for this user. */
    fun recipient(user: User, to: String?): String {
        val requested = to?.trim()?.takeIf { it.isNotEmpty() } ?: return user.email
        if (requested.equals(user.email.trim(), ignoreCase = true)) return user.email
        if (user.role == Role.ADMIN) return requested
        throw RequestException(HttpStatus.BAD_REQUEST, "The statement can only be sent to your own email address")
    }

    /** Counts one manual send; 429 once the user is over [DAILY_LIMIT] in the window. */
    fun consume(userId: UUID) {
        val key = "statement:mail:$userId"
        val count = try {
            val c = redis.opsForValue().increment(key) ?: 1L
            if (c == 1L) redis.expire(key, WINDOW)
            c
        } catch (ex: Exception) {
            log.warn("Statement mail limiter unavailable, refusing the send: {}", ex.message)
            throw RequestException(HttpStatus.SERVICE_UNAVAILABLE, "Sending is unavailable right now, try again later")
        }
        if (count > DAILY_LIMIT) {
            throw RequestException(HttpStatus.TOO_MANY_REQUESTS, "You can send the statement $DAILY_LIMIT times a day. Try again tomorrow.")
        }
    }

    companion object {
        const val DAILY_LIMIT = 3L
        val WINDOW: Duration = Duration.ofHours(24)
    }
}
