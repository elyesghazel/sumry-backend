package ch.sumry.core.module.user.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.plan.PlanService
import ch.sumry.core.module.porting.ImportChecker
import ch.sumry.core.module.user.dto.auth.MeDto
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.security.Principal
import java.util.*

/** Builds GET /api/users/me from the user and the per-user settings of other modules. */
@Service
class MeService(
    private val users: UserRepository,
    private val plans: PlanService,
    private val checker: ImportChecker,
) {
    fun me(principal: Principal): MeDto {
        val userId = UUID.fromString(principal.name)
        val user = users.findById(userId)
            .orElseThrow { RequestException(HttpStatus.NOT_FOUND, "User not found") }
        val plan = plans.effective(user)
        return MeDto(
            id = user.id,
            username = user.username,
            email = user.email,
            role = user.role,
            plan = plan.plan,
            limitsEnabled = plan.limitsEnabled,
            limits = plan.limits,
            aiEnabled = checker.enabledFor(userId),
        )
    }
}
