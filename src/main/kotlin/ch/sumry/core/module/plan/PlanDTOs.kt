package ch.sumry.core.module.plan

import ch.sumry.core.module.user.model.Plan
import jakarta.validation.constraints.NotNull
import java.util.*

data class PlanUpdateDto(
    @field:NotNull
    val plan: Plan?,
)

data class UserPlanDto(
    val userId: UUID,
    /** The plan stored on the user. */
    val plan: Plan,
    /** The plan the user is treated as on this instance (premium while limits are off). */
    val effectivePlan: Plan,
    val limits: PlanLimits,
)
