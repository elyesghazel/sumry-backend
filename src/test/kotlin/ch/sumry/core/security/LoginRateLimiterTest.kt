package ch.sumry.core.security

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.registration.RegistrationPolicy
import ch.sumry.core.module.user.dto.auth.LoginDto
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.module.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.*

class LoginRateLimiterTest {

    /** In-memory stand-in for the few Redis calls the limiter makes. */
    private val store = mutableMapOf<String, Long>()
    private val ops = mockk<ValueOperations<String, String>>().also { ops ->
        every { ops.increment(any()) } answers { store.merge(firstArg(), 1L, Long::plus) }
        every { ops.get(any()) } answers { store[firstArg<String>()]?.toString() }
    }
    private val redis = mockk<StringRedisTemplate> {
        every { opsForValue() } returns ops
        every { expire(any(), any<java.time.Duration>()) } returns true
        every { getExpire(any()) } returns 900L
        every { delete(any<String>()) } answers { store.remove(firstArg<String>()) != null }
    }
    private val limiter = LoginRateLimiter(redis)

    private fun request(ip: String = "203.0.113.7") = mockk<HttpServletRequest> {
        every { getHeader("X-Forwarded-For") } returns ip
        every { remoteAddr } returns "10.0.0.1"
    }

    private val users = mockk<UserRepository>(relaxed = true).also { users ->
        every { users.save(any<User>()) } answers { firstArg() }
    }
    private val service = UserService(
        users, mockk(relaxed = true), limiter, mockk(relaxed = true),
        RegistrationPolicy("open", false), mockk(relaxed = true), mockk(relaxed = true),
    )

    private fun user(email: String) = User(
        id = UUID.randomUUID(), username = email.substringBefore('@'), email = email,
        password = BCryptPasswordEncoder().encode("right-password"),
    ).also { every { users.findByEmail(email) } returns it }

    @Test
    fun `31 successful sign-ins from one address still work`() {
        val people = (1..4).map { user("u$it@example.com") }
        repeat(40) { i ->
            val email = people[i % people.size].email
            assertNotNull(service.login(LoginDto(email, "right-password"), request()))
        }
    }

    @Test
    fun `failed sign-ins from one address lock that address`() {
        val req = request()
        repeat(30) { i ->
            val email = "nobody$i@example.com"
            val ex = assertThrows<RequestException> { service.login(LoginDto(email, "guess"), req) }
            assertEquals(HttpStatus.UNAUTHORIZED, ex.status)
        }
        val victim = user("victim@example.com")
        val ex = assertThrows<RequestException> { service.login(LoginDto(victim.email, "right-password"), req) }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.status)
        assertNotNull(service.login(LoginDto(victim.email, "right-password"), request("198.51.100.9")))
    }

    @Test
    fun `ten wrong passwords lock the email, a success before that clears it`() {
        val target = user("target@example.com")
        repeat(9) { i ->
            assertThrows<RequestException> { service.login(LoginDto(target.email, "wrong"), request("192.0.2.$i")) }
        }
        service.login(LoginDto(target.email, "right-password"), request("192.0.2.100"))
        assertNull(limiter.retryAfterSeconds(request("192.0.2.101"), target.email))

        repeat(10) { i ->
            assertThrows<RequestException> { service.login(LoginDto(target.email, "wrong"), request("192.0.2.$i")) }
        }
        val ex = assertThrows<RequestException> {
            service.login(LoginDto(target.email, "right-password"), request("192.0.2.200"))
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.status)
    }

    @Test
    fun `the signup limit still counts every attempt`() {
        val req = request()
        repeat(5) { assertNull(limiter.signupRetryAfterSeconds(req)) }
        assertNotNull(limiter.signupRetryAfterSeconds(req))
    }
}
