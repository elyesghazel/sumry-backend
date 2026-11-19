package ch.sumry.core.module.plan

import ch.sumry.core.module.user.model.Plan
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.*

class PlanServiceTest {

    private val userId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val users: UserRepository = mockk(relaxed = true)
    private val accounts: UserAccountRepository = mockk(relaxed = true)

    private fun service(enabled: Boolean, maxAccounts: Int = 2) = PlanService(users, accounts, enabled, maxAccounts)

    private fun stubUser(plan: Plan) {
        every { users.findById(userId) } returns Optional.of(
            User(id = userId, username = "u", email = "u@test.ch", password = "x", plan = plan)
        )
    }

    // --- limits off (self-hosted default) -------------------------------------------------

    @Test
    fun `limits off treats a free user as premium without loading it`() {
        val plans = service(enabled = false)

        val effective = plans.effective(userId)

        assertEquals(Plan.PREMIUM, effective.plan)
        assertFalse(effective.limitsEnabled)
        assertEquals(PlanLimits.UNLIMITED, effective.limits)
        verify(exactly = 0) { users.findById(any<UUID>()) }
    }

    @Test
    fun `limits off never blocks accounts, import or statement email`() {
        val plans = service(enabled = false)
        every { accounts.countByUserId(userId) } returns 50

        assertDoesNotThrow {
            plans.requireAccountSlot(userId)
            plans.requireStatementImport(userId)
            plans.requireStatementEmail(userId)
        }
    }

    // --- limits on (hosted) ---------------------------------------------------------------

    @Test
    fun `limits on exposes the free limits`() {
        stubUser(Plan.FREE)

        val effective = service(enabled = true, maxAccounts = 3).effective(userId)

        assertEquals(Plan.FREE, effective.plan)
        assertTrue(effective.limitsEnabled)
        assertEquals(PlanLimits(maxAccounts = 3, statementImport = false, statementEmail = false), effective.limits)
    }

    @Test
    fun `limits on lets a free user create accounts below the cap`() {
        stubUser(Plan.FREE)
        every { accounts.countByUserId(userId) } returns 1

        assertDoesNotThrow { service(enabled = true).requireAccountSlot(userId) }
    }

    @Test
    fun `limits on blocks a free user at the account cap`() {
        stubUser(Plan.FREE)
        every { accounts.countByUserId(userId) } returns 2

        val ex = assertThrows<PlanLimitException> { service(enabled = true).requireAccountSlot(userId) }
        assertEquals(PlanLimit.ACCOUNTS, ex.limit)
    }

    @Test
    fun `limits on blocks import and statement email for a free user`() {
        stubUser(Plan.FREE)
        val plans = service(enabled = true)

        assertEquals(PlanLimit.STATEMENT_IMPORT, assertThrows<PlanLimitException> { plans.requireStatementImport(userId) }.limit)
        assertEquals(PlanLimit.STATEMENT_EMAIL, assertThrows<PlanLimitException> { plans.requireStatementEmail(userId) }.limit)
    }

    @Test
    fun `limits on lets a premium user do everything`() {
        stubUser(Plan.PREMIUM)
        every { accounts.countByUserId(userId) } returns 50
        val plans = service(enabled = true)

        assertEquals(PlanLimits.UNLIMITED, plans.effective(userId).limits)
        assertDoesNotThrow {
            plans.requireAccountSlot(userId)
            plans.requireStatementImport(userId)
            plans.requireStatementEmail(userId)
        }
    }

    @Test
    fun `setPlan stores the new plan`() {
        stubUser(Plan.FREE)
        every { users.save(any()) } answers { firstArg() }

        val saved = service(enabled = true).setPlan(userId, Plan.PREMIUM)

        assertEquals(Plan.PREMIUM, saved.plan)
        verify { users.save(match { it.plan == Plan.PREMIUM }) }
    }
}
