package ch.sumry.core.module.plan

import ch.sumry.core.module.user.model.Plan

/**
 * What a user may do under their effective plan. A null limit means unlimited.
 * New limits are added as fields here so clients see them in /api/users/me.
 */
data class PlanLimits(
    val maxAccounts: Int?,
    val statementImport: Boolean,
    val statementEmail: Boolean,
) {
    companion object {
        val UNLIMITED = PlanLimits(maxAccounts = null, statementImport = true, statementEmail = true)
    }
}

/** The plan a user is treated as right now, and its limits. */
data class EffectivePlan(
    val plan: Plan,
    /** False on instances that do not enforce plans (self-hosted); then everyone is premium. */
    val limitsEnabled: Boolean,
    val limits: PlanLimits,
)

/** Names used in the `limit` field of a 403 plan_limit response. */
object PlanLimit {
    const val ACCOUNTS = "accounts"
    const val STATEMENT_IMPORT = "statement_import"
    const val STATEMENT_EMAIL = "statement_email"
}

/** Thrown when a free user hits a limit; answered as 403 {"error":"plan_limit","limit":"..."}. */
class PlanLimitException(val limit: String) : RuntimeException("Plan limit reached: $limit")
