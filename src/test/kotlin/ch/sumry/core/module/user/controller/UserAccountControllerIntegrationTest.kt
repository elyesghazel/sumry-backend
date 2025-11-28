package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.accounts.UserAccountCreateDto
import ch.sumry.core.module.user.dto.accounts.UserAccountResponseDto
import ch.sumry.core.module.user.dto.accounts.UserAccountUpdateDto
import ch.sumry.core.module.user.service.UserAccountService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

// Assuming your Controller is mapped to /api/users/accounts
@WebMvcTest(UserAccountController::class)
class UserAccountControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userAccountService: UserAccountService

    @TestConfiguration
    class ControllerTestConfig {
        @Bean
        fun userAccountService() = io.mockk.mockk<UserAccountService>()
    }

    // --- 1. GET Test (Already Working) ---

    @Test
    @WithMockUser(username = "testUser", roles = ["USER"])
    fun `should get account by id and return 200 OK`() {
        // ARRANGE
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val accountResponseDto = UserAccountResponseDto(accountId, userId, "Test Account", 100.0)

        every { userAccountService.findById(accountId) } returns accountResponseDto

        // ACT & ASSERT
        mockMvc.perform(get("/api/users/accounts/id/{accountId}", accountId)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(accountId.toString()))
            .andExpect(jsonPath("$.accountName").value("Test Account"))
            .andExpect(jsonPath("$.currentBalance").value(100.0))

        verify(exactly = 1) { userAccountService.findById(accountId) }
    }

    // --- 2. POST Test ---

    @Test
    @WithMockUser(username = "testUser", roles = ["USER"])
    fun `should create a new account and return 201 Created`() {
        // ARRANGE
        val userId = UUID.randomUUID()
        val newAccountId = UUID.randomUUID()
        val accountCreateDto = UserAccountCreateDto(
            userId = userId,
            accountName = "New Savings",
            currentBalance = 500.0,
        )
        val expectedResponseDto = UserAccountResponseDto(
            id = newAccountId,
            userId = userId,
            accountName = "New Savings",
            currentBalance = 500.0
        )

        // Mock the service call, expecting the DTO passed to the controller
        every { userAccountService.create(accountCreateDto) } returns expectedResponseDto

        // ACT & ASSERT
        mockMvc.perform(post("/api/users/accounts")
            .with(csrf()) // Essential for POST/PUT/DELETE if CSRF protection is enabled
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(accountCreateDto)))
            .andExpect(status().isCreated()) // HTTP 201
            .andExpect(jsonPath("$.id").value(newAccountId.toString()))
            .andExpect(jsonPath("$.currentBalance").value(500.0))

        verify(exactly = 1) { userAccountService.create(accountCreateDto) }
    }

    // --- 3. PUT/PATCH Test ---

    @Test
    @WithMockUser(username = "testUser", roles = ["USER"])
    fun `should update account balance and return 200 OK`() {
        // ARRANGE
        val accountId = UUID.randomUUID()
        val updateDto = UserAccountUpdateDto(currentBalance = 150.0)
        val userId = UUID.randomUUID()

        // Mock the response after update
        val expectedResponseDto = UserAccountResponseDto(
            id = accountId,
            userId = userId,
            accountName = "Test Account",
            currentBalance = 150.0 // The updated value
        )

        every { userAccountService.updateBalance(accountId, updateDto) } returns expectedResponseDto

        // ACT & ASSERT (Assuming a PUT or PATCH mapping to /id/{accountId})
        mockMvc.perform(put("/api/users/accounts/balance/{accountId}", accountId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateDto)))
            .andExpect(status().isOk()) // HTTP 200
            .andExpect(jsonPath("$.id").value(accountId.toString()))
            .andExpect(jsonPath("$.currentBalance").value(150.0)) // Verify the updated balance

        verify(exactly = 1) { userAccountService.updateBalance(accountId, updateDto) }
    }

    // --- 4. DELETE Test ---

    @Test
    @WithMockUser(username = "testUser", roles = ["USER"])
    fun `should delete an account and return 204 No Content`() {
        // ARRANGE
        val accountId = UUID.randomUUID()
        // Mock the service to do nothing (Unit)
        every { userAccountService.delete(accountId) } returns true

        // ACT & ASSERT
        mockMvc.perform(delete("/api/users/accounts/id/{accountId}", accountId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent()) // HTTP 204
            .andExpect(content().string("")) // Expect an empty body

        verify(exactly = 1) { userAccountService.delete(accountId) }
    }
}