package ch.sumry.core.module.admin

import ch.sumry.core.support.DatabaseIntegrationTest
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

private const val ADMIN_EMAIL = "admin@example.com"
private const val ADMIN_PASSWORD = "AdminPassword12"
private const val USER_EMAIL = "alice@example.com"
private const val USER_PASSWORD = "AlicePassword12"

class AdminIntegrationTest : DatabaseIntegrationTest() {

    @Autowired private lateinit var objectMapper: ObjectMapper

    private lateinit var adminId: UUID
    private lateinit var aliceId: UUID
    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        adminId = createUser(ADMIN_EMAIL, ADMIN_PASSWORD, role = "ADMIN")
        aliceId = createUser(USER_EMAIL, USER_PASSWORD)
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD)
    }

    private fun MockHttpServletRequestBuilder.auth(token: String) = header("Authorization", "Bearer $token")

    private fun call(request: MockHttpServletRequestBuilder, token: String? = adminToken): ResultActions =
        mockMvc.perform(if (token != null) request.auth(token) else request)

    private fun json(request: MockHttpServletRequestBuilder, token: String? = adminToken): JsonNode =
        objectMapper.readTree(call(request, token).andExpect(status().isOk).andReturn().response.contentAsString)

    /** Two accounts with recognisable balances and three transactions with notes. */
    private fun seedFinances(userId: UUID) {
        val category = UUID.randomUUID()
        jdbc.update("INSERT INTO categories (id, label, category_type, user_id) VALUES (?, 'Food', 'EXPENSE', ?)", category, userId)
        repeat(2) { i ->
            val account = UUID.randomUUID()
            jdbc.update(
                "INSERT INTO user_accounts (id, account_name, current_balance, user_id) VALUES (?, ?, 98765.43, ?)",
                account, "Account $i", userId,
            )
            if (i == 0) repeat(3) {
                jdbc.update(
                    "INSERT INTO transactions (id, amount, kind, description, category_id, user_account_id, base_amount) " +
                        "VALUES (?, 4242.42, 'EXPENSE', 'SECRET-NOTE-TEXT', ?, ?, 4242.42)",
                    UUID.randomUUID(), category, account,
                )
            }
        }
    }

    private fun userRow(users: JsonNode, id: UUID): JsonNode = users.first { it["id"].asText() == id.toString() }

    private fun auditActions(): List<String> =
        json(get("/api/admin/audit"))["items"].map { it["action"].asText() }

    // --- access ---

    @Test
    fun `every admin route refuses a normal user and an anonymous caller`() {
        val userToken = login(USER_EMAIL, USER_PASSWORD)
        // Builders collect headers, so each call gets fresh ones.
        val routes = { listOf(
            get("/api/admin/users"),
            get("/api/admin/stats"),
            get("/api/admin/audit"),
            post("/api/admin/users/$adminId/lock"),
            post("/api/admin/users/$adminId/unlock"),
            delete("/api/admin/users/$adminId"),
            post("/api/admin/users/$adminId/password-reset"),
            post("/api/admin/users/$aliceId/plan").contentType(MediaType.APPLICATION_JSON).content("""{"plan":"PREMIUM"}"""),
        ) }
        routes().forEach { call(it, userToken).andExpect(status().isForbidden) }
        routes().forEach { call(it, token = null).andExpect(status().isUnauthorized) }
        // Nothing happened to anyone.
        assertEquals(0, count("SELECT count(*) FROM admin_audit"))
        assertEquals(0, count("SELECT count(*) FROM users WHERE locked_at IS NOT NULL"))
        assertEquals(2, count("SELECT count(*) FROM users"))
    }

    @Test
    fun `there is no route to sign in as another user`() {
        listOf("impersonate", "login-as", "token", "session").forEach {
            call(post("/api/admin/users/$aliceId/$it")).andExpect(status().isNotFound)
        }
    }

    // --- users ---

    @Test
    fun `the user list shows profile, status and counts`() {
        seedFinances(aliceId)
        jdbc.update(
            "INSERT INTO invite_codes (id, code_hash, code_hint, used_by, used_at) VALUES (?, 'h', 'WXYZ', ?, now())",
            UUID.randomUUID(), aliceId,
        )
        login(USER_EMAIL, USER_PASSWORD)

        val users = json(get("/api/admin/users"))
        assertEquals(2, users.size())
        val alice = userRow(users, aliceId)
        assertEquals("alice", alice["username"].asText())
        assertEquals(USER_EMAIL, alice["email"].asText())
        assertEquals("USER", alice["role"].asText())
        assertEquals("FREE", alice["plan"].asText())
        assertEquals("active", alice["status"].asText())
        assertEquals("WXYZ", alice["inviteCodeHint"].asText())
        assertEquals(2, alice["accountCount"].asLong())
        assertEquals(3, alice["transactionCount"].asLong())
        assertFalse(alice["createdAt"].isNull)
        assertFalse(alice["lastLoginAt"].isNull)
        assertTrue(alice["lockedAt"].isNull)
        assertTrue(userRow(users, adminId)["inviteCodeHint"].isNull)
    }

    @Test
    fun `stats count users, premium and signups this week`() {
        jdbc.update("UPDATE users SET plan = 'PREMIUM' WHERE id = ?", aliceId)
        jdbc.update("UPDATE users SET locked_at = now() WHERE id = ?", aliceId)

        val stats = json(get("/api/admin/stats"))
        assertEquals(2, stats["totalUsers"].asLong())
        assertEquals(1, stats["premiumUsers"].asLong())
        assertEquals(1, stats["lockedUsers"].asLong())
        assertTrue(stats["databaseSizeBytes"].asLong() > 0)
        val weeks = stats["signupsPerWeek"]
        assertEquals(12, weeks.size())
        assertEquals(2, weeks.last()["signups"].asLong())
        assertEquals(0, weeks.first()["signups"].asLong())
    }

    // --- plan ---

    @Test
    fun `setting and unsetting premium is audited`() {
        call(post("/api/admin/users/$aliceId/plan").contentType(MediaType.APPLICATION_JSON).content("""{"plan":"PREMIUM"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.plan").value("PREMIUM"))
        call(post("/api/admin/users/$aliceId/plan").contentType(MediaType.APPLICATION_JSON).content("""{"plan":"FREE"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.plan").value("FREE"))

        val items = json(get("/api/admin/audit"))["items"]
        assertEquals(listOf("PLAN_SET", "PLAN_SET"), items.map { it["action"].asText() })
        assertEquals(setOf("PREMIUM", "FREE"), items.map { it["detail"]["plan"].asText() }.toSet())
        assertTrue(items.all { it["targetUserId"].asText() == aliceId.toString() && it["targetUsername"].asText() == "alice" })
        assertTrue(items.all { it["adminId"].asText() == adminId.toString() && it["adminUsername"].asText() == "admin" })
    }

    // --- lock ---

    @Test
    fun `locking revokes an existing token at once and refuses the login`() {
        val token = login(USER_EMAIL, USER_PASSWORD)
        // Prime the token cache, as any real session would have.
        call(get("/api/users/validate"), token).andExpect(status().isOk)

        call(post("/api/admin/users/$aliceId/lock"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("locked"))

        call(get("/api/users/validate"), token).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$USER_EMAIL","password":"$USER_PASSWORD"}""")
        ).andExpect(status().isForbidden)
        // A wrong password still answers 401, so the lock is not revealed to a guesser.
        mockMvc.perform(
            post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$USER_EMAIL","password":"wrong-password"}""")
        ).andExpect(status().isUnauthorized)

        call(post("/api/admin/users/$aliceId/unlock"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("active"))
        val fresh = login(USER_EMAIL, USER_PASSWORD)
        call(get("/api/users/validate"), fresh).andExpect(status().isOk)
        // The token from before the lock stays dead.
        call(get("/api/users/validate"), token).andExpect(status().isUnauthorized)

        assertEquals(listOf("UNLOCK", "LOCK"), auditActions())
    }

    @Test
    fun `an admin cannot lock or delete themselves`() {
        call(post("/api/admin/users/$adminId/lock")).andExpect(status().isBadRequest)
        call(delete("/api/admin/users/$adminId")).andExpect(status().isBadRequest)
        call(get("/api/users/validate")).andExpect(status().isOk)
    }

    @Test
    fun `an unknown user is not found`() {
        val nobody = UUID.randomUUID()
        call(post("/api/admin/users/$nobody/lock")).andExpect(status().isNotFound)
        call(delete("/api/admin/users/$nobody")).andExpect(status().isNotFound)
        call(post("/api/admin/users/$nobody/password-reset")).andExpect(status().isNotFound)
        assertEquals(0, count("SELECT count(*) FROM admin_audit"))
    }

    // --- delete ---

    @Test
    fun `deleting a user removes their data and keeps the audit history`() {
        seedFinances(aliceId)
        val token = login(USER_EMAIL, USER_PASSWORD)
        call(post("/api/admin/users/$aliceId/plan").contentType(MediaType.APPLICATION_JSON).content("""{"plan":"PREMIUM"}"""))
            .andExpect(status().isOk)
        call(post("/api/admin/users/$aliceId/password-reset")).andExpect(status().isOk)

        call(delete("/api/admin/users/$aliceId")).andExpect(status().isNoContent)

        assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", aliceId))
        assertEquals(0, count("SELECT count(*) FROM user_accounts WHERE user_id = ?", aliceId))
        assertEquals(0, count("SELECT count(*) FROM password_reset_tokens WHERE user_id = ?", aliceId))
        call(get("/api/users/validate"), token).andExpect(status().isUnauthorized)

        val items = json(get("/api/admin/audit"))["items"]
        assertEquals(listOf("DELETE_USER", "PASSWORD_RESET", "PLAN_SET"), items.map { it["action"].asText() })
        assertTrue(items.all { it["targetUserId"].asText() == aliceId.toString() })
        assertTrue(items.all { it["targetUsername"].isNull })
    }

    // --- password reset ---

    @Test
    fun `a reset token sets a new password once and signs out everywhere`() {
        val oldToken = login(USER_EMAIL, USER_PASSWORD)
        val reset = json(post("/api/admin/users/$aliceId/password-reset"))
        assertEquals(aliceId.toString(), reset["userId"].asText())
        assertFalse(reset["expiresAt"].isNull)
        val resetToken = reset["token"].asText()
        assertEquals(0, count("SELECT count(*) FROM password_reset_tokens WHERE token_hash = ?", resetToken))

        val body = """{"token":"$resetToken","newPassword":"BrandNewPassword1"}"""
        mockMvc.perform(post("/api/users/password-reset").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNoContent)

        call(get("/api/users/validate"), oldToken).andExpect(status().isUnauthorized)
        login(USER_EMAIL, "BrandNewPassword1")
        mockMvc.perform(post("/api/users/password-reset").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
        assertEquals(listOf("PASSWORD_RESET"), auditActions())
    }

    @Test
    fun `a new reset token replaces the old one and expired tokens fail`() {
        val first = json(post("/api/admin/users/$aliceId/password-reset"))["token"].asText()
        val second = json(post("/api/admin/users/$aliceId/password-reset"))["token"].asText()

        mockMvc.perform(post("/api/users/password-reset").contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"$first","newPassword":"BrandNewPassword1"}""")).andExpect(status().isBadRequest)

        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'")
        mockMvc.perform(post("/api/users/password-reset").contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"$second","newPassword":"BrandNewPassword1"}""")).andExpect(status().isBadRequest)
        login(USER_EMAIL, USER_PASSWORD)
    }

    // --- audit ---

    @Test
    fun `the audit log is paged newest first and records invite actions`() {
        val invite = objectMapper.readTree(
            call(post("/api/admin/invites").contentType(MediaType.APPLICATION_JSON).content("""{"note":"for lea"}"""))
                .andExpect(status().isCreated).andReturn().response.contentAsString
        )
        call(delete("/api/admin/invites/${invite["id"].asText()}")).andExpect(status().isOk)
        call(post("/api/admin/users/$aliceId/lock")).andExpect(status().isOk)

        val first = json(get("/api/admin/audit?page=0&size=2"))
        assertEquals(3, first["totalItems"].asLong())
        assertEquals(2, first["totalPages"].asInt())
        assertEquals(listOf("LOCK", "INVITE_REVOKE"), first["items"].map { it["action"].asText() })
        val second = json(get("/api/admin/audit?page=1&size=2"))
        assertEquals(listOf("INVITE_CREATE"), second["items"].map { it["action"].asText() })
        val created = second["items"][0]
        assertTrue(created["targetUserId"].isNull)
        assertEquals(invite["id"].asText(), created["detail"]["inviteId"].asText())
        // Neither the code nor the admin's free-text label lands in the log.
        assertFalse(created["detail"].has("code"))
        assertFalse(created.toString().contains("for lea"))
    }

    // --- privacy ---

    /**
     * The core rule of the admin area: nothing from a user's finances. Checks every field
     * name in every response, and that the seeded amounts and notes appear nowhere.
     */
    @Test
    fun `admin users, stats and audit responses contain no amounts, balances or notes`() {
        seedFinances(aliceId)
        seedFinances(adminId)
        // Fill the audit log with every kind of action.
        call(post("/api/admin/users/$aliceId/plan").contentType(MediaType.APPLICATION_JSON).content("""{"plan":"PREMIUM"}"""))
            .andExpect(status().isOk)
        call(post("/api/admin/users/$aliceId/lock")).andExpect(status().isOk)
        call(post("/api/admin/users/$aliceId/unlock")).andExpect(status().isOk)
        call(post("/api/admin/users/$aliceId/password-reset")).andExpect(status().isOk)
        call(post("/api/admin/invites").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated)
        val bob = createUser("bob@example.com", "BobPassword1234")
        seedFinances(bob)
        call(delete("/api/admin/users/$bob")).andExpect(status().isNoContent)

        val responses = mapOf(
            "users" to get("/api/admin/users"),
            "stats" to get("/api/admin/stats"),
            "audit" to get("/api/admin/audit"),
            "lock" to post("/api/admin/users/$aliceId/lock"),
            "unlock" to post("/api/admin/users/$aliceId/unlock"),
        ).mapValues { (_, request) -> call(request).andExpect(status().isOk).andReturn().response.contentAsString }

        val forbidden = Regex("amount|balance|note|description|iban|currency", RegexOption.IGNORE_CASE)
        responses.forEach { (name, body) ->
            val keys = fieldNames(objectMapper.readTree(body))
            assertTrue(keys.isNotEmpty(), "$name returned no fields")
            val leaked = keys.filter { forbidden.containsMatchIn(it) }
            assertTrue(leaked.isEmpty(), "$name exposes $leaked")
            listOf("4242.42", "98765.43", "SECRET-NOTE-TEXT", "Account 0").forEach {
                assertFalse(body.contains(it), "$name contains $it")
            }
        }
    }

    private fun fieldNames(node: JsonNode): Set<String> = when {
        node.isObject -> node.fields().asSequence().flatMap { (k, v) -> sequenceOf(k) + fieldNames(v) }.toSet()
        node.isArray -> node.flatMap { fieldNames(it) }.toSet()
        else -> emptySet()
    }
}
