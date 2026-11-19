package ch.sumry.core.module.statement

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.model.Role
import ch.sumry.core.module.user.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import java.util.*

class StatementMailPolicyTest {

    private val userId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ops = mockk<ValueOperations<String, String>>()
    private val redis = mockk<StringRedisTemplate>(relaxed = true) {
        every { opsForValue() } returns ops
    }
    private val policy = StatementMailPolicy(redis)

    private fun user(role: Role = Role.USER) =
        User(id = userId, username = "alice", email = "Alice@Example.com", password = "x").also { it.role = role }

    @Test
    fun `no address means the account email`() {
        assertEquals("Alice@Example.com", policy.recipient(user(), null))
        assertEquals("Alice@Example.com", policy.recipient(user(), "  "))
    }

    @Test
    fun `own address is allowed in any case`() {
        assertEquals("Alice@Example.com", policy.recipient(user(), " alice@example.COM "))
    }

    @Test
    fun `a user cannot mail someone else`() {
        val e = assertThrows<RequestException> { policy.recipient(user(), "victim@example.org") }
        assertEquals(HttpStatus.BAD_REQUEST, e.status)
    }

    @Test
    fun `an admin may mail another address`() {
        assertEquals("ops@example.org", policy.recipient(user(Role.ADMIN), "ops@example.org"))
    }

    @Test
    fun `three sends a day pass, the fourth is refused`() {
        var n = 0L
        every { ops.increment("statement:mail:$userId") } answers { ++n }
        repeat(3) { assertDoesNotThrow { policy.consume(userId) } }
        val e = assertThrows<RequestException> { policy.consume(userId) }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.status)
        verify(exactly = 1) { redis.expire("statement:mail:$userId", StatementMailPolicy.WINDOW) }
    }

    @Test
    fun `without redis the send is refused`() {
        every { ops.increment(any()) } throws IllegalStateException("redis down")
        val e = assertThrows<RequestException> { policy.consume(userId) }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.status)
    }
}
