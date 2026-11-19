package ch.sumry.core.module.user.service

import ch.sumry.core.support.DatabaseIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

class AccountDeletionIntegrationTest : DatabaseIntegrationTest() {

    /** One row in every table that holds user data. */
    private fun seed(userId: UUID) {
        val account = UUID.randomUUID()
        val category = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO user_preferences (id, user_id, app_lock, lock_delay_seconds) VALUES (?, ?, false, 0)",
            UUID.randomUUID(), userId,
        )
        jdbc.update("INSERT INTO user_accounts (id, account_name, current_balance, user_id) VALUES (?, 'Main', 100, ?)", account, userId)
        jdbc.update("INSERT INTO categories (id, label, category_type, user_id) VALUES (?, 'Food', 'EXPENSE', ?)", category, userId)
        jdbc.update(
            "INSERT INTO transactions (id, amount, kind, category_id, user_account_id, base_amount) VALUES (?, 12.5, 'EXPENSE', ?, ?, 12.5)",
            UUID.randomUUID(), category, account,
        )
        jdbc.update("INSERT INTO user_budgets (id, max_amount, category_id, user_id) VALUES (?, 300, ?, ?)", UUID.randomUUID(), category, userId)
        jdbc.update(
            "INSERT INTO planned_transactions (id, amount, name, category_id, user_id, user_account_id, to_account_id) VALUES (?, 50, 'Rent', ?, ?, ?, ?)",
            UUID.randomUUID(), category, userId, account, account,
        )
        jdbc.update(
            "INSERT INTO category_rules (id, user_id, pattern, category_id, account_id) VALUES (?, ?, 'coop', ?, ?)",
            UUID.randomUUID(), userId, category, account,
        )
        jdbc.update(
            "INSERT INTO goals (id, user_id, name, target_amount, account_id) VALUES (?, ?, 'Bike', 1000, ?)",
            UUID.randomUUID(), userId, account,
        )
        jdbc.update(
            "INSERT INTO invite_codes (id, code_hash, code_hint, created_by, used_by) VALUES (?, ?, 'ABCD', ?, ?)",
            UUID.randomUUID(), UUID.randomUUID().toString(), userId, userId,
        )
    }

    private fun rowsOf(userId: UUID): Map<String, Int> = mapOf(
        "users" to count("SELECT count(*) FROM users WHERE id = ?", userId),
        "user_preferences" to count("SELECT count(*) FROM user_preferences WHERE user_id = ?", userId),
        "user_accounts" to count("SELECT count(*) FROM user_accounts WHERE user_id = ?", userId),
        "categories" to count("SELECT count(*) FROM categories WHERE user_id = ?", userId),
        "transactions" to count(
            "SELECT count(*) FROM transactions t JOIN user_accounts a ON a.id = t.user_account_id WHERE a.user_id = ?", userId,
        ),
        "user_budgets" to count("SELECT count(*) FROM user_budgets WHERE user_id = ?", userId),
        "planned_transactions" to count("SELECT count(*) FROM planned_transactions WHERE user_id = ?", userId),
        "category_rules" to count("SELECT count(*) FROM category_rules WHERE user_id = ?", userId),
        "goals" to count("SELECT count(*) FROM goals WHERE user_id = ?", userId),
        "invite_codes" to count("SELECT count(*) FROM invite_codes WHERE created_by = ? OR used_by = ?", userId, userId),
    )

    private fun deleteMe(token: String, password: String) = mockMvc.perform(
        delete("/api/users/me").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"password":"$password"}""")
    )

    @Test
    fun `deleting the account leaves no rows for that user and keeps everyone else's`() {
        val alice = createUser("alice@example.com", "AlicePassword12")
        val bob = createUser("bob@example.com", "BobPassword1234")
        seed(alice)
        seed(bob)
        val token = login("alice@example.com", "AlicePassword12")
        val totalTransactions = count("SELECT count(*) FROM transactions")

        deleteMe(token, "AlicePassword12").andExpect(status().isNoContent)

        rowsOf(alice).forEach { (table, rows) -> assertEquals(0, rows, "rows left in $table") }
        rowsOf(bob).forEach { (table, rows) -> assertEquals(1, rows, "bob lost rows in $table") }
        assertEquals(totalTransactions - 1, count("SELECT count(*) FROM transactions"))
        // The invite rows stay for the admin's list, only the references are cleared.
        assertEquals(2, count("SELECT count(*) FROM invite_codes"))
    }

    @Test
    fun `the token stops working once the account is gone`() {
        createUser("alice@example.com", "AlicePassword12")
        val token = login("alice@example.com", "AlicePassword12")
        // Prime the token cache, as any real session would have.
        mockMvc.perform(get("/api/users/validate").header("Authorization", "Bearer $token")).andExpect(status().isOk)

        deleteMe(token, "AlicePassword12").andExpect(status().isNoContent)

        mockMvc.perform(get("/api/users/validate").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a wrong password deletes nothing`() {
        val alice = createUser("alice@example.com", "AlicePassword12")
        seed(alice)
        val token = login("alice@example.com", "AlicePassword12")

        deleteMe(token, "not-the-password").andExpect(status().isUnauthorized)

        rowsOf(alice).forEach { (table, rows) -> assertEquals(1, rows, "row lost in $table") }
    }

    @Test
    fun `a missing password is a bad request`() {
        createUser("alice@example.com", "AlicePassword12")
        val token = login("alice@example.com", "AlicePassword12")

        mockMvc.perform(
            delete("/api/users/me").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isBadRequest)
        assertEquals(1, count("SELECT count(*) FROM users"))
    }
}
