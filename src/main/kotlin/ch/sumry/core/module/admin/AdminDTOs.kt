package ch.sumry.core.module.admin

import ch.sumry.core.module.user.model.Plan
import ch.sumry.core.module.user.model.Role
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * One row of the admin user list. Counts only: no amounts, balances, notes, descriptions or
 * anything else from a user's finances. AdminIntegrationTest checks the field names.
 */
data class AdminUserDto(
    val id: UUID,
    val username: String,
    val email: String,
    val role: Role,
    val plan: Plan,
    /** `active` or `locked`. */
    val status: String,
    /** Null for users created before signup dates were recorded. */
    val createdAt: Instant?,
    val lastLoginAt: Instant?,
    val lockedAt: Instant?,
    /** Hint (first characters) of the invite code the user signed up with, or null. */
    val inviteCodeHint: String?,
    val accountCount: Long,
    val transactionCount: Long,
)

data class SignupWeekDto(
    /** Monday of the ISO week. */
    val weekStart: LocalDate,
    val signups: Long,
)

data class AdminStatsDto(
    val totalUsers: Long,
    val lockedUsers: Long,
    /** Users whose stored plan is PREMIUM, whether or not limits are enabled. */
    val premiumUsers: Long,
    /** The last 12 ISO weeks, oldest first, including the current one. */
    val signupsPerWeek: List<SignupWeekDto>,
    val databaseSizeBytes: Long,
)

data class AdminAuditEntryDto(
    val id: UUID,
    val action: AdminAction,
    val adminId: UUID,
    /** Null once that admin has been deleted. */
    val adminUsername: String?,
    val targetUserId: UUID?,
    /** Null when there is no target or the target has been deleted. */
    val targetUsername: String?,
    val detail: Map<String, Any?>,
    val createdAt: Instant,
)

data class AdminAuditPageDto(
    val items: List<AdminAuditEntryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

data class PasswordResetTokenDto(
    val userId: UUID,
    /** The one-time token. Shown only in this response; only a hash is stored. */
    val token: String,
    val expiresAt: Instant,
)

enum class AdminAction {
    PLAN_SET,
    LOCK,
    UNLOCK,
    DELETE_USER,
    PASSWORD_RESET,
    INVITE_CREATE,
    INVITE_REVOKE,
}
