package ch.sumry.core.module.user.controller

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.accounts.UserAccountCreateDto
import ch.sumry.core.module.user.dto.accounts.UserAccountResponseDto
import ch.sumry.core.module.user.dto.accounts.UserAccountUpdateDto
import ch.sumry.core.module.user.service.UserAccountService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

private const val OWNER_USER_ID_STRING = "11111111-2222-3333-4444-555555555555"
private val OWNER_USER_UUID = UUID.fromString(OWNER_USER_ID_STRING)

private const val MALICIOUS_USER_ID_STRING = "99999999-8888-7777-6666-555555555555"

@WebMvcTest(UserAccountController::class)
class UserAccountControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var userAccountService: UserAccountService

    @TestConfiguration
    class ControllerTestConfig {
        @Bean
        fun userAccountService(): UserAccountService = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        clearMocks(userAccountService)
    }

    // =========================================================================================
    // 1. HAPPY PATH TESTS
    // =========================================================================================

    @Test
    @WithMockUser(username = OWNER_USER_ID_STRING, roles = ["USER"])
    fun `should get account by id and return 200 OK`() {
        val accountId = UUID.randomUUID()
        val accountResponseDto = UserAccountResponseDto(accountId, OWNER_USER_UUID, "Test Account", 100.0)

        every {
            userAccountService.findById(
                accountId,
                match { it.name == OWNER_USER_ID_STRING }
            )
        } returns accountResponseDto

        mockMvc.perform(get("/api/users/accounts/id/{accountId}", accountId).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(accountId.toString()))
            .andExpect(jsonPath("$.currentBalance").value(100.0))

        verify(exactly = 1) {
            userAccountService.findById(
                accountId,
                match { it.name == OWNER_USER_ID_STRING }
            )
        }
    }

    @Test
    @WithMockUser(username = OWNER_USER_ID_STRING, roles = ["USER"])
    fun `should create a new account and return 201 Created`() {
        val newAccountId = UUID.randomUUID()
        val accountCreateDto = UserAccountCreateDto(accountName = "New Savings", currentBalance = 500.0)
        val expectedResponseDto = UserAccountResponseDto(newAccountId, OWNER_USER_UUID, "New Savings", 500.0)

        every {
            userAccountService.create(
                accountCreateDto,
                match { it.name == OWNER_USER_ID_STRING }
            )
        } returns expectedResponseDto

        mockMvc.perform(
            post("/api/users/accounts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(accountCreateDto))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(newAccountId.toString()))
            .andExpect(jsonPath("$.userId").value(OWNER_USER_ID_STRING))

        verify(exactly = 1) {
            userAccountService.create(
                accountCreateDto,
                match { it.name == OWNER_USER_ID_STRING }
            )
        }
    }

    @Test
    @WithMockUser(username = OWNER_USER_ID_STRING, roles = ["USER"])
    fun `should update account balance and return 200 OK`() {
        val accountId = UUID.randomUUID()
        val updateDto = UserAccountUpdateDto(currentBalance = 150.0)
        val expectedResponseDto = UserAccountResponseDto(accountId, OWNER_USER_UUID, "Test Account", 150.0)

        every {
            userAccountService.updateBalance(
                accountId,
                updateDto,
                match { it.name == OWNER_USER_ID_STRING }
            )
        } returns expectedResponseDto

        mockMvc.perform(
            put("/api/users/accounts/balance/{accountId}", accountId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentBalance").value(150.0))

        verify(exactly = 1) {
            userAccountService.updateBalance(
                accountId,
                updateDto,
                match { it.name == OWNER_USER_ID_STRING }
            )
        }
    }

    @Test
    @WithMockUser(username = OWNER_USER_ID_STRING, roles = ["USER"])
    fun `should delete an account and return 204 No Content`() {
        val accountId = UUID.randomUUID()

        every {
            userAccountService.delete(
                accountId,
                match { it.name == OWNER_USER_ID_STRING }
            )
        } returns true

        mockMvc.perform(
            delete("/api/users/accounts/{accountId}", accountId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNoContent())

        verify(exactly = 1) {
            userAccountService.delete(
                accountId,
                match { it.name == OWNER_USER_ID_STRING }
            )
        }
    }

    // =========================================================================================
    // 2. ERROR TESTS
    // =========================================================================================

    @Test
    fun `should return 401 Unauthorized when accessing secured endpoint without authentication`() {
        val accountId = UUID.randomUUID()
        mockMvc.perform(get("/api/users/accounts/id/{accountId}", accountId).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = OWNER_USER_ID_STRING, roles = ["USER"])
    fun `should return 404 Not Found when account does not exist`() {
        val nonExistentId = UUID.randomUUID()

        every { userAccountService.findById(nonExistentId, match { it.name == OWNER_USER_ID_STRING }) } answers {
            throw NoSuchElementException("Account not found")
        }

        mockMvc.perform(
            get(
                "/api/users/accounts/id/{accountId}",
                nonExistentId
            ).contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)

        verify(exactly = 1) { userAccountService.findById(nonExistentId, match { it.name == OWNER_USER_ID_STRING }) }
    }

    @Test
    @WithMockUser(username = OWNER_USER_ID_STRING, roles = ["USER"])
    fun `should return 400 Bad Request on invalid create DTO`() {
        val invalidDto = UserAccountCreateDto(accountName = "", currentBalance = 50.0)

        mockMvc.perform(
            post("/api/users/accounts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto))
        )
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { userAccountService.create(any(), any()) }
    }

    // =========================================================================================
    // 3. FORBIDDEN (403) TESTS - Resource Ownership Check
    // =========================================================================================

    @Test
    @WithMockUser(username = MALICIOUS_USER_ID_STRING, roles = ["USER"])
    fun `should return 403 Forbidden when requesting principal does not own the account`() {
        val accountId = UUID.randomUUID()

        every {
            userAccountService.findById(
                accountId,
                match { it.name == MALICIOUS_USER_ID_STRING }
            )
        } answers {
            throw RequestException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        mockMvc.perform(
            get("/api/users/accounts/id/{accountId}", accountId)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").exists())

        verify(exactly = 1) {
            userAccountService.findById(
                accountId,
                match { it.name == MALICIOUS_USER_ID_STRING }
            )
        }
    }

    @Test
    @WithMockUser(username = MALICIOUS_USER_ID_STRING, roles = ["USER"])
    fun `should return 403 Forbidden when updating a balance the principal does not own`() {
        val accountId = UUID.randomUUID()
        val updateDto = UserAccountUpdateDto(currentBalance = 500.0)

        every {
            userAccountService.updateBalance(
                accountId,
                updateDto,
                match { it.name == MALICIOUS_USER_ID_STRING }
            )
        } answers {
            throw RequestException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        mockMvc.perform(
            put("/api/users/accounts/balance/{accountId}", accountId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isForbidden)

        verify(exactly = 1) {
            userAccountService.updateBalance(
                accountId,
                updateDto,
                match { it.name == MALICIOUS_USER_ID_STRING }
            )
        }
    }

    @Test
    @WithMockUser(username = MALICIOUS_USER_ID_STRING, roles = ["USER"])
    fun `should return 403 Forbidden when deleting an account the principal does not own`() {
        val accountId = UUID.randomUUID()

        every {
            userAccountService.delete(
                accountId,
                match { it.name == MALICIOUS_USER_ID_STRING }
            )
        } answers {
            throw RequestException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        mockMvc.perform(
            delete("/api/users/accounts/{accountId}", accountId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)

        verify(exactly = 1) {
            userAccountService.delete(
                accountId,
                match { it.name == MALICIOUS_USER_ID_STRING }
            )
        }
    }
}
