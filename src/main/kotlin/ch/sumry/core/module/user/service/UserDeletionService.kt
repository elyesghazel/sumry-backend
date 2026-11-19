package ch.sumry.core.module.user.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Removes a user and everything they own in one transaction.
 *
 * Plain SQL in foreign-key order rather than JPA cascades: most tables only point at the
 * user, the entities do not own them, and a cascade that misses one would leave rows behind.
 * A table added later that references users and is not listed here makes the final delete
 * fail on its foreign key, so the whole deletion rolls back instead of leaving data.
 */
@Service
class UserDeletionService(private val jdbc: JdbcTemplate) {

    @Transactional
    fun deleteAllData(userId: UUID) {
        val accounts = "SELECT id FROM user_accounts WHERE user_id = ?"
        val categories = "SELECT id FROM categories WHERE user_id = ?"

        jdbc.update(
            "DELETE FROM transactions WHERE user_account_id IN ($accounts) OR category_id IN ($categories)",
            userId, userId,
        )
        jdbc.update("DELETE FROM category_rules WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM goals WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM planned_transactions WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM user_budgets WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM user_preferences WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM user_accounts WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM categories WHERE user_id = ?", userId)
        // Invite codes keep their row for the admin's list; the user references are cleared.
        jdbc.update("UPDATE invite_codes SET created_by = NULL WHERE created_by = ?", userId)
        jdbc.update("UPDATE invite_codes SET used_by = NULL WHERE used_by = ?", userId)
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId)
        // admin_audit rows are kept: their user ids have no foreign key and simply stop
        // resolving to a username, so the history of admin actions survives the deletion.
        jdbc.update("DELETE FROM users WHERE id = ?", userId)
    }
}
