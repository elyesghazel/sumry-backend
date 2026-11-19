package ch.sumry.core.module.admin

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.model.Plan
import ch.sumry.core.module.user.model.Role
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.module.user.service.PasswordResetService
import ch.sumry.core.module.user.service.UserDeletionService
import ch.sumry.core.security.TokenRevocationService
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * User management for admins. Reads go through SQL that selects counts and profile fields
 * only, so no amount, balance or note can end up in a response by accident.
 * There is deliberately no way to sign in as another user.
 */
@Service
class AdminUserService(
    private val jdbc: JdbcTemplate,
    private val users: UserRepository,
    private val revocation: TokenRevocationService,
    private val deletion: UserDeletionService,
    private val passwordResets: PasswordResetService,
    private val audit: AdminAuditService,
) {

    fun list(): List<AdminUserDto> = query("", emptyArray())

    fun get(id: UUID): AdminUserDto =
        query("WHERE u.id = ?", arrayOf(id)).firstOrNull() ?: throw RequestException(HttpStatus.NOT_FOUND, "User not found")

    private fun query(where: String, args: Array<Any>): List<AdminUserDto> = jdbc.query(
        """
        SELECT u.id, u.username, u.email, u.role, u.plan, u.created_at, u.last_login_at, u.locked_at,
               (SELECT count(*) FROM user_accounts a WHERE a.user_id = u.id) AS account_count,
               (SELECT count(*) FROM transactions t JOIN user_accounts a ON a.id = t.user_account_id
                 WHERE a.user_id = u.id) AS transaction_count,
               (SELECT i.code_hint FROM invite_codes i WHERE i.used_by = u.id ORDER BY i.used_at LIMIT 1) AS invite_code_hint
        FROM users u
        $where
        ORDER BY u.created_at DESC NULLS LAST, u.username
        """,
        { rs, _ -> row(rs) },
        *args,
    )

    private fun row(rs: java.sql.ResultSet): AdminUserDto {
        val lockedAt = rs.getTimestamp("locked_at")?.toInstant()
        return AdminUserDto(
            id = rs.getObject("id", UUID::class.java),
            username = rs.getString("username") ?: "",
            email = rs.getString("email") ?: "",
            role = rs.getString("role")?.let { Role.valueOf(it) } ?: Role.USER,
            plan = Plan.valueOf(rs.getString("plan")),
            status = if (lockedAt != null) STATUS_LOCKED else STATUS_ACTIVE,
            createdAt = rs.getTimestamp("created_at")?.toInstant(),
            lastLoginAt = rs.getTimestamp("last_login_at")?.toInstant(),
            lockedAt = lockedAt,
            inviteCodeHint = rs.getString("invite_code_hint"),
            accountCount = rs.getLong("account_count"),
            transactionCount = rs.getLong("transaction_count"),
        )
    }

    fun stats(): AdminStatsDto {
        val totals = jdbc.queryForMap(
            """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE locked_at IS NOT NULL) AS locked,
                   count(*) FILTER (WHERE plan = 'PREMIUM') AS premium
            FROM users
            """,
        )
        val weeks = jdbc.query(
            """
            SELECT w.week::date AS week_start, count(u.id) AS signups
            FROM generate_series(date_trunc('week', now()) - interval '11 weeks', date_trunc('week', now()), interval '1 week') AS w(week)
            LEFT JOIN users u ON date_trunc('week', u.created_at) = w.week
            GROUP BY w.week
            ORDER BY w.week
            """,
        ) { rs, _ -> SignupWeekDto(rs.getObject("week_start", LocalDate::class.java), rs.getLong("signups")) }
        val dbSize = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long::class.java) ?: 0L
        return AdminStatsDto(
            totalUsers = (totals["total"] as Number).toLong(),
            lockedUsers = (totals["locked"] as Number).toLong(),
            premiumUsers = (totals["premium"] as Number).toLong(),
            signupsPerWeek = weeks,
            databaseSizeBytes = dbSize,
        )
    }

    /**
     * Locks the account: login is refused and every token already issued (sessions and
     * Shortcut tokens) stops working at once through the token version bump.
     */
    fun lock(adminId: UUID, id: UUID): AdminUserDto {
        refuseSelf(adminId, id, "lock")
        val user = load(id)
        if (user.lockedAt == null) {
            user.lockedAt = Instant.now()
            users.save(user)
        }
        revocation.revokeAll(id)
        // After the commit, so a request racing the lock cannot re-cache the old state.
        revocation.forget(id)
        audit.record(adminId, AdminAction.LOCK, id)
        return get(id)
    }

    fun unlock(adminId: UUID, id: UUID): AdminUserDto {
        val user = load(id)
        if (user.lockedAt != null) {
            user.lockedAt = null
            users.save(user)
        }
        revocation.forget(id)
        audit.record(adminId, AdminAction.UNLOCK, id)
        return get(id)
    }

    fun delete(adminId: UUID, id: UUID) {
        refuseSelf(adminId, id, "delete")
        load(id)
        deletion.deleteAllData(id)
        revocation.forget(id)
        audit.record(adminId, AdminAction.DELETE_USER, id)
    }

    fun createPasswordReset(adminId: UUID, id: UUID): PasswordResetTokenDto {
        val issued = passwordResets.create(id)
        audit.record(adminId, AdminAction.PASSWORD_RESET, id, mapOf("expiresAt" to issued.expiresAt.toString()))
        return PasswordResetTokenDto(userId = id, token = issued.token, expiresAt = issued.expiresAt)
    }

    /** Own account: use the normal account routes, so an admin cannot lock themselves out. */
    private fun refuseSelf(adminId: UUID, id: UUID, what: String) {
        if (adminId == id) throw RequestException(HttpStatus.BAD_REQUEST, "You cannot $what your own account here")
    }

    private fun load(id: UUID): User =
        users.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "User not found") }

    private companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_LOCKED = "locked"
    }
}
