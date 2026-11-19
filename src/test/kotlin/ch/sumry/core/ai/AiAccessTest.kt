package ch.sumry.core.ai

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.util.*

class AiAccessTest {

    private val owner = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val other = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val users = mockk<UserRepository>().also {
        every { it.findById(owner) } returns Optional.of(User(owner, "owner", "Owner@Example.com", "x"))
        every { it.findById(other) } returns Optional.of(User(other, "other", "other@example.com", "x"))
    }

    @Test
    fun `empty list turns ai off for everyone`() {
        val access = AiAccess("", users)
        assertFalse(access.isAllowed(owner))
        assertFalse(access.isAllowed(other))
        verify(exactly = 0) { users.findById(any<UUID>()) }
    }

    @Test
    fun `blank entries count as empty`() {
        assertFalse(AiAccess(" , ,", users).isAllowed(owner))
    }

    @Test
    fun `allows listed user ids`() {
        val access = AiAccess(" $owner ", users)
        assertTrue(access.isAllowed(owner))
        assertFalse(access.isAllowed(other))
        verify(exactly = 0) { users.findById(any<UUID>()) }
    }

    @Test
    fun `allows listed emails ignoring case`() {
        val access = AiAccess("nobody@example.com, owner@example.COM", users)
        assertTrue(access.isAllowed(owner))
        assertFalse(access.isAllowed(other))
    }

    @Test
    fun `unknown user is not allowed`() {
        val missing = UUID.randomUUID()
        every { users.findById(missing) } returns Optional.empty()
        assertFalse(AiAccess("owner@example.com", users).isAllowed(missing))
    }

    @Test
    fun `requireAllowed answers 403`() {
        val e = assertThrows<RequestException> { AiAccess(owner.toString(), users).requireAllowed(other) }
        assertEquals(HttpStatus.FORBIDDEN, e.status)
    }
}
