package ch.sumry.core.module.user.service

import ch.sumry.core.module.plan.PlanService
import ch.sumry.core.module.user.model.Plan
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.Principal
import java.util.*

class MeServiceTest {

    private val userId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val users: UserRepository = mockk {
        every { findById(userId) } returns Optional.of(
            User(id = userId, username = "owner", email = "owner@test.ch", password = "x", plan = Plan.FREE)
        )
    }
    private val accounts: UserAccountRepository = mockk(relaxed = true)

    private fun me(limitsEnabled: Boolean) =
        MeService(users, PlanService(users, accounts, limitsEnabled, 2)).me(Principal { userId.toString() })

    @Test
    fun `existing user is premium with no limits while limits are off`() {
        val me = me(limitsEnabled = false)

        assertEquals("owner", me.username)
        assertEquals(Plan.PREMIUM, me.plan)
        assertFalse(me.limitsEnabled)
        assertNull(me.limits.maxAccounts)
        assertTrue(me.limits.statementImport)
        assertTrue(me.limits.statementEmail)
    }

    @Test
    fun `free user sees free limits while limits are on`() {
        val me = me(limitsEnabled = true)

        assertEquals(Plan.FREE, me.plan)
        assertTrue(me.limitsEnabled)
        assertEquals(2, me.limits.maxAccounts)
        assertFalse(me.limits.statementImport)
        assertFalse(me.limits.statementEmail)
    }
}
