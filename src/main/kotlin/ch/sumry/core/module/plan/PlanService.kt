package ch.sumry.core.module.plan

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.model.Plan
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.*

/**
 * Plan limits for the hosted instance. Off by default: with `sumry.limits.enabled=false`
 * every user is treated as premium and nothing is checked, whatever their stored plan.
 */
@Service
class PlanService(
    private val users: UserRepository,
    private val accounts: UserAccountRepository,
    @Value("\${sumry.limits.enabled:false}") private val limitsEnabled: Boolean,
    @Value("\${sumry.limits.free-max-accounts:2}") private val freeMaxAccounts: Int,
) {

    private val unlimited = EffectivePlan(Plan.PREMIUM, limitsEnabled = false, limits = PlanLimits.UNLIMITED)

    fun effective(user: User): EffectivePlan =
        if (!limitsEnabled) unlimited
        else EffectivePlan(user.plan, limitsEnabled = true, limits = limitsFor(user.plan))

    /** Skips the user lookup entirely while limits are off. */
    fun effective(userId: UUID): EffectivePlan =
        if (!limitsEnabled) unlimited else effective(loadUser(userId))

    fun limitsFor(plan: Plan): PlanLimits = when (plan) {
        Plan.PREMIUM -> PlanLimits.UNLIMITED
        Plan.FREE -> PlanLimits(maxAccounts = freeMaxAccounts, statementImport = false, statementEmail = false)
    }

    /** Call before creating an account. */
    fun requireAccountSlot(userId: UUID) {
        val max = effective(userId).limits.maxAccounts ?: return
        if (accounts.countByUserId(userId) >= max) throw PlanLimitException(PlanLimit.ACCOUNTS)
    }

    fun requireStatementImport(userId: UUID) {
        if (!effective(userId).limits.statementImport) throw PlanLimitException(PlanLimit.STATEMENT_IMPORT)
    }

    fun requireStatementEmail(userId: UUID) {
        if (!effective(userId).limits.statementEmail) throw PlanLimitException(PlanLimit.STATEMENT_EMAIL)
    }

    /** Admin unlock without payment. Works whether or not limits are enabled. */
    fun setPlan(userId: UUID, plan: Plan): User {
        val user = loadUser(userId)
        user.plan = plan
        return users.save(user)
    }

    private fun loadUser(userId: UUID): User =
        users.findById(userId).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "User not found") }
}
