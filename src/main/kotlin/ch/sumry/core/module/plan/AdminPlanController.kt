package ch.sumry.core.module.plan

import ch.sumry.core.module.admin.AdminAction
import ch.sumry.core.module.admin.AdminAuditService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/admin/users")
class AdminPlanController(
    private val plans: PlanService,
    private val audit: AdminAuditService,
) {

    /** Sets a user's plan by hand, e.g. to unlock premium without a payment. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/plan")
    fun setPlan(@PathVariable id: UUID, @Valid @RequestBody dto: PlanUpdateDto): UserPlanDto {
        val user = plans.setPlan(id, dto.plan!!)
        // From the security context rather than a Principal parameter, so the slice test needs no filter chain.
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.name)
        audit.record(adminId, AdminAction.PLAN_SET, id, mapOf("plan" to user.plan.name))
        val effective = plans.effective(user)
        return UserPlanDto(userId = id, plan = user.plan, effectivePlan = effective.plan, limits = effective.limits)
    }
}
