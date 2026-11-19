package ch.sumry.core.module.user.dto.auth

import ch.sumry.core.module.plan.PlanLimits
import ch.sumry.core.module.user.model.Plan
import java.util.*

/**
 * The signed-in user plus what this instance lets them do. Per-user feature flags go
 * here as new fields, so clients only have one place to look.
 */
data class MeDto(
    val id: UUID?,
    val username: String,
    val email: String,
    /** Effective plan: PREMIUM for everyone while limits are off. */
    val plan: Plan,
    val limitsEnabled: Boolean,
    val limits: PlanLimits,
    /** Same value as /api/users/me/capabilities: on the AI allowlist and the bridge is configured. */
    val aiEnabled: Boolean,
)
