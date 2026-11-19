package ch.sumry.core.module.category

import ch.sumry.core.module.category.service.DefaultCategories
import ch.sumry.core.support.DatabaseIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@TestPropertySource(properties = ["sumry.registration.mode=open"])
class DefaultCategoriesIntegrationTest : DatabaseIntegrationTest() {

    private fun signUp(name: String): String {
        register("""{"username":"$name","email":"$name@example.com","password":"SecurePassword12"}""")
            .andExpect(status().isOk)
        return login("$name@example.com", "SecurePassword12")
    }

    @Test
    fun `a new account starts with the default categories`() {
        val token = signUp("alice")

        mockMvc.perform(get("/api/category/user").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(DefaultCategories.DEFAULTS.size))
        assertEquals(1, count("SELECT count(*) FROM categories WHERE label = 'Eating out' AND category_type = 'EXPENSE'"))
        assertEquals(1, count("SELECT count(*) FROM categories WHERE label = 'Salary' AND category_type = 'INCOME'"))
    }

    private fun addCash(token: String): String {
        val body = mockMvc.perform(
            post("/api/users/accounts").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"accountName":"Cash","currentBalance":100}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun quick(token: String, text: String, book: Boolean) = mockMvc.perform(
        post("/api/transactions/quick").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"text":"$text","book":$book}""")
    )

    @Test
    fun `quick add books a lunch on a brand-new account`() {
        val token = signUp("alice")
        addCash(token)

        quick(token, "12.50 lunch cash", book = true)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ready").value(true))
            .andExpect(jsonPath("$.parsed.categoryLabel").value("Eating out"))
            .andExpect(jsonPath("$.parsed.accountName").value("Cash"))
            .andExpect(jsonPath("$.booked").exists())
        assertEquals(1, count("SELECT count(*) FROM transactions"))
    }

    @Test
    fun `the user's own rules win over the starter rules`() {
        val token = signUp("alice")
        addCash(token)
        mockMvc.perform(
            post("/api/rules").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pattern":"lunch","categoryLabel":"Groceries"}""")
        ).andExpect(status().is2xxSuccessful)

        quick(token, "12.50 lunch cash", book = false)
            .andExpect(jsonPath("$.parsed.categoryLabel").value("Groceries"))
    }

    @Test
    fun `accounts that already exist are not seeded`() {
        createUser("bob@example.com", "SecurePassword12")
        signUp("alice")
        assertEquals(0, count("SELECT count(*) FROM categories c JOIN users u ON u.id = c.user_id WHERE u.email = 'bob@example.com'"))
    }
}
