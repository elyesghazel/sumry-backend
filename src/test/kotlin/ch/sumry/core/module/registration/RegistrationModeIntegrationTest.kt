package ch.sumry.core.module.registration

import ch.sumry.core.support.DatabaseIntegrationTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private fun signup(name: String, invite: String? = null) =
    """{"username":"$name","email":"$name@example.com","password":"SecurePassword12"${
        invite?.let { ",\"inviteCode\":\"$it\"" } ?: ""
    }}"""

/** No mode and no legacy flag: the default every existing deployment has. */
class ClosedRegistrationIntegrationTest : DatabaseIntegrationTest() {

    @Test
    fun `config reports closed`() {
        mockMvc.perform(get("/api/config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.registrationMode").value("closed"))
    }

    @Test
    fun `register is refused and creates nothing`() {
        register(signup("alice")).andExpect(status().isForbidden)
        assertEquals(0, count("SELECT count(*) FROM users"))
    }

    @Test
    fun `an invite code does not open a closed instance`() {
        register(signup("alice", "ANYTHING")).andExpect(status().isForbidden)
        assertEquals(0, count("SELECT count(*) FROM users"))
    }
}

/** SUMRY_REGISTRATION_ENABLED=true without a mode keeps meaning open. */
@TestPropertySource(properties = ["sumry.registration.enabled=true"])
class LegacyRegistrationFlagIntegrationTest : DatabaseIntegrationTest() {

    @Test
    fun `legacy flag opens registration`() {
        mockMvc.perform(get("/api/config")).andExpect(jsonPath("$.registrationMode").value("open"))
        register(signup("alice")).andExpect(status().isOk)
        assertEquals(1, count("SELECT count(*) FROM users"))
    }
}

/** An explicit mode wins over the legacy flag. */
@TestPropertySource(properties = ["sumry.registration.enabled=true", "sumry.registration.mode=closed"])
class ModeOverridesLegacyFlagIntegrationTest : DatabaseIntegrationTest() {

    @Test
    fun `mode wins over legacy flag`() {
        mockMvc.perform(get("/api/config")).andExpect(jsonPath("$.registrationMode").value("closed"))
        register(signup("alice")).andExpect(status().isForbidden)
    }
}

@TestPropertySource(properties = ["sumry.registration.mode=open"])
class OpenRegistrationIntegrationTest : DatabaseIntegrationTest() {

    @Test
    fun `config reports open`() {
        mockMvc.perform(get("/api/config")).andExpect(jsonPath("$.registrationMode").value("open"))
    }

    @Test
    fun `anyone can register and sign in`() {
        register(signup("alice")).andExpect(status().isOk).andExpect(jsonPath("$.username").value("alice"))
        login("alice@example.com", "SecurePassword12")
    }

    @Test
    fun `signups are rate limited per client address`() {
        repeat(5) { i -> register(signup("user$i"), clientIp = "198.51.100.7").andExpect(status().isOk) }
        register(signup("user5"), clientIp = "198.51.100.7")
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error", containsString("Too many signups")))
        // Another address is not affected.
        register(signup("user6"), clientIp = "198.51.100.8").andExpect(status().isOk)
        assertEquals(6, count("SELECT count(*) FROM users"))
    }
}

@TestPropertySource(properties = ["sumry.registration.mode=invite"])
class InviteRegistrationIntegrationTest : DatabaseIntegrationTest() {

    private fun adminToken(): String {
        createUser("admin@example.com", "AdminPassword12", role = "ADMIN")
        return login("admin@example.com", "AdminPassword12")
    }

    private fun createInvite(token: String, body: String = "{}"): String {
        val response = mockMvc.perform(
            post("/api/admin/invites").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return Regex("\"code\"\\s*:\\s*\"([A-Z0-9]+)\"").find(response)!!.groupValues[1]
    }

    private fun inviteId(): String = jdbc.queryForObject("SELECT id::text FROM invite_codes", String::class.java)!!

    @Test
    fun `config reports invite`() {
        mockMvc.perform(get("/api/config")).andExpect(jsonPath("$.registrationMode").value("invite"))
    }

    @Test
    fun `register without a code is refused`() {
        register(signup("alice")).andExpect(status().isForbidden)
        assertEquals(0, count("SELECT count(*) FROM users"))
    }

    @Test
    fun `an unknown code is refused before the duplicate checks`() {
        createUser("alice@example.com", "SomePassword123")
        // A taken email would answer 409; without a valid code it must not reveal that.
        register(signup("alice", "NOTAREALCODE")).andExpect(status().isForbidden)
    }

    @Test
    fun `a code works once`() {
        val code = createInvite(adminToken())

        // Lowercase and with dashes: codes are typed on phones.
        register(signup("alice", code.lowercase().chunked(4).joinToString("-"))).andExpect(status().isOk)
        login("alice@example.com", "SecurePassword12")
        assertEquals(1, count("SELECT count(*) FROM invite_codes WHERE used_at IS NOT NULL AND used_by IS NOT NULL"))

        register(signup("bob", code)).andExpect(status().isForbidden)
        assertEquals(0, count("SELECT count(*) FROM users WHERE username = 'bob'"))
    }

    @Test
    fun `a revoked code is refused`() {
        val token = adminToken()
        val code = createInvite(token)
        mockMvc.perform(delete("/api/admin/invites/${inviteId()}").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("revoked"))

        register(signup("alice", code)).andExpect(status().isForbidden)
    }

    @Test
    fun `an expired code is refused`() {
        val code = createInvite(adminToken(), """{"expiresInDays":1}""")
        jdbc.update("UPDATE invite_codes SET expires_at = now() - interval '1 minute'")

        register(signup("alice", code)).andExpect(status().isForbidden)
    }

    @Test
    fun `the list shows codes without the secret`() {
        val token = adminToken()
        val code = createInvite(token, """{"note":"for lea"}""")

        mockMvc.perform(get("/api/admin/invites").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].code").doesNotExist())
            .andExpect(jsonPath("$[0].codeHint").value(code.take(4)))
            .andExpect(jsonPath("$[0].note").value("for lea"))
            .andExpect(jsonPath("$[0].status").value("active"))
        assertNull(jdbc.queryForList("SELECT code_hash FROM invite_codes", String::class.java).find { it == code })
    }

    @Test
    fun `only admins manage invites`() {
        createUser("user@example.com", "UserPassword123")
        val token = login("user@example.com", "UserPassword123")

        mockMvc.perform(post("/api/admin/invites").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/admin/invites").header("Authorization", "Bearer $token"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/admin/invites")).andExpect(status().isUnauthorized)
    }
}
