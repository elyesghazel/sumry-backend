package ch.sumry.core.module.user.service

import ch.sumry.core.module.category.service.DefaultCategories
import ch.sumry.core.module.user.model.Role
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class BootstrapAdminTest {

    private val repo: UserRepository = mockk(relaxed = true)
    private val defaults: DefaultCategories = mockk(relaxed = true)

    @Test
    fun `does nothing without an email`() {
        assertNull(BootstrapAdmin(repo, defaults, " ").create())
        verify(exactly = 0) { repo.save(any<User>()) }
    }

    @Test
    fun `does nothing once users exist`() {
        every { repo.count() } returns 1
        assertNull(BootstrapAdmin(repo, defaults, "me@example.com").create())
        verify(exactly = 0) { repo.save(any<User>()) }
        verify(exactly = 0) { defaults.seed(any()) }
    }

    @Test
    fun `creates an admin with a hashed one-time password on an empty instance`() {
        every { repo.count() } returns 0
        val saved = slot<User>()
        every { repo.save(capture(saved)) } answers { saved.captured }

        val password = BootstrapAdmin(repo, defaults, "me@example.com").create()

        assertNotNull(password)
        assertEquals(24, password!!.length)
        with(saved.captured) {
            assertEquals("me@example.com", email)
            assertEquals("me", username)
            assertEquals(Role.ADMIN, role)
            assertTrue(BCryptPasswordEncoder().matches(password, this.password))
        }
        verify(exactly = 1) { defaults.seed(saved.captured) }
    }
}
