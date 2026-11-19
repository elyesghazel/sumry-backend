package ch.sumry.core.module.plan

import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/admin/users")
class AdminPlanController(private val plans: PlanService) {

    /** Sets a user's plan by hand, e.g. to unlock premium without a payment. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/plan")
    fun setPlan(@PathVariable id: UUID, @Valid @RequestBody dto: PlanUpdateDto): UserPlanDto {
        val user = plans.setPlan(id, dto.plan!!)
        val effective = plans.effective(user)
        return UserPlanDto(userId = id, plan = user.plan, effectivePlan = effective.plan, limits = effective.limits)
    }
}
