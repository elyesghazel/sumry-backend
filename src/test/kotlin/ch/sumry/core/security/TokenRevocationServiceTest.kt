package ch.sumry.core.security

import ch.sumry.core.module.user.model.Role
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.*

class TokenRevocationServiceTest {

    private val userId = UUID.randomUUID()
    private val ops = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redis = mockk<StringRedisTemplate> {
        every { hasKey(any()) } returns false
        every { opsForValue() } returns ops
    }
    private val users = mockk<UserRepository>()
    private val service = TokenRevocationService(redis, users)

    private fun claims(role: String?, version: Int = 0) =
        TokenClaims(userId.toString(), "jti", version, role, Date(System.currentTimeMillis() + 60_000))

    private fun dbUser(role: Role) {
        every { ops.get(any()) } returns null
        every { users.findById(userId) } returns Optional.of(
            User(id = userId, username = "u", email = "u@x", password = "x", role = role)
        )
    }

    @Test
    fun `a forged admin claim gets the database role`() {
        dbUser(Role.USER)
        assertEquals("USER", service.activeRole(claims("ADMIN")))
    }

    @Test
    fun `an admin keeps admin without the claim`() {
        dbUser(Role.ADMIN)
        assertEquals("ADMIN", service.activeRole(claims(null)))
    }

    @Test
    fun `a stale token version is refused`() {
        dbUser(Role.USER)
        assertNull(service.activeRole(claims("USER", version = 1)))
    }

    @Test
    fun `a cached state is used without a database read`() {
        every { ops.get(any()) } returns "0:USER"
        assertEquals("USER", service.activeRole(claims("ADMIN")))
    }
}
