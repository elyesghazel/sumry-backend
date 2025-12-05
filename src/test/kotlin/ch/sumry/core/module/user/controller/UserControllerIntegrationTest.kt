package ch.sumry.core.module.user.controller

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.auth.LoginDto
import ch.sumry.core.module.user.dto.auth.LoginResponseDto
import ch.sumry.core.module.user.dto.auth.UserCreateDto
import ch.sumry.core.module.user.dto.auth.UserResponseDto
import ch.sumry.core.module.user.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

@WebMvcTest(UserController::class)
class UserControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService

    @TestConfiguration
    class ControllerTestConfig {
        @Bean
        fun userService(): UserService = mockk(relaxed = true)
    }

    private val TEST_USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val TEST_USER_ID_STRING = TEST_USER_ID.toString()
    private val USER_RESPONSE = UserResponseDto(TEST_USER_ID, "testuser", "test@example.com")
    private val LOGIN_DTO = LoginDto("test@example.com", "Password123")


    // -------------------------------------------------------------------------
    // A. REGISTER TESTS (Public Endpoint)
    // -------------------------------------------------------------------------

    @Test
    fun `register should create user and return 200 OK`() {
        // ARRANGE
        val createDto = UserCreateDto("newuser", "new@example.com", "SecurePwd1")
        every { userService.register(any()) } returns UserResponseDto(UUID.randomUUID(), "newuser", "new@example.com")

        // ACT & ASSERT
        mockMvc.perform(post("/api/users/register")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createDto)))
            .andExpect(status().isOk) // Or 201 CREATED if you change the controller to return ResponseEntity.created
            .andExpect(jsonPath("$.username").value("newuser"))

        verify(exactly = 1) { userService.register(any()) }
    }

    @Test
    fun `register should return 409 Conflict if user exists`() {
        // ARRANGE
        val createDto = UserCreateDto("exist", "exist@e.com", "SecurePwd1")
        every { userService.register(any()) } throws RequestException(HttpStatus.CONFLICT, "Username already taken")

        // ACT & ASSERT
        mockMvc.perform(post("/api/users/register")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createDto)))
            .andExpect(status().isConflict)
    }

    @Test
    fun `register should return 400 Bad Request on invalid DTO`() {
        // ARRANGE - Missing email, violating DTO constraints
        val invalidDto = mapOf("username" to "user", "password" to "short") // Missing required fields

        // ACT & ASSERT
        mockMvc.perform(post("/api/users/register")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidDto)))
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { userService.register(any()) }
    }

    // -------------------------------------------------------------------------
    // B. LOGIN TESTS (Public Endpoint)
    // -------------------------------------------------------------------------

    @Test
    fun `login should return 200 OK and JWT on success`() {
        // ARRANGE
        val responseDto = LoginResponseDto("mocked.jwt.token", LOGIN_DTO.email)
        every { userService.login(any()) } returns responseDto

        // ACT & ASSERT
        mockMvc.perform(post("/api/users/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(LOGIN_DTO)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("mocked.jwt.token"))
    }

    @Test
    fun `login should return 401 Unauthorized on failure`() {
        // ARRANGE
        every { userService.login(any()) } throws RequestException(HttpStatus.UNAUTHORIZED, "Invalid email or password")

        // ACT & ASSERT
        mockMvc.perform(post("/api/users/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(LOGIN_DTO)))
            .andExpect(status().isUnauthorized)
    }

    // -------------------------------------------------------------------------
    // C. SECURED ENDPOINT TESTS
    // -------------------------------------------------------------------------


    @Test
    fun `getAll should return 401 Unauthorized when unauthenticated`() {
        // ACT & ASSERT
        mockMvc.perform(get("/api/users")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getById should return 200 OK when user exists`() {
        // ARRANGE
        every { userService.getById(TEST_USER_ID) } returns USER_RESPONSE

        // ACT & ASSERT
        mockMvc.perform(get("/api/users/{id}", TEST_USER_ID_STRING)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(USER_RESPONSE.username))
    }
}