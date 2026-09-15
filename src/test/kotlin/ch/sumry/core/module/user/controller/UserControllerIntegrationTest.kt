package ch.sumry.core.module.user.controller
import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.auth.*
import ch.sumry.core.module.user.service.UserService
import ch.sumry.core.security.MethodSecurityTestConfig
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.security.Principal
import java.util.*

// constants
private const val ADMIN_ID_STRING = "11111111-1111-1111-1111-111111111111"
private const val USER_ID_STRING = "22222222-2222-2222-2222-222222222222"
private val USER_ID_UUID = UUID.fromString(USER_ID_STRING)
private val USER_RESPONSE = UserResponseDto(USER_ID_UUID, "testuser", "test@example.com")
private val LOGIN_DTO = LoginDto("test@example.com", "Password123")
private val userMockPrincipal = Principal { USER_ID_STRING }
private val otherMockPrincipal = Principal { ADMIN_ID_STRING }

@WebMvcTest(
    controllers = [UserController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class]
)

// import for @preauthorize to work
@Import(MethodSecurityTestConfig::class)
class UserControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService

    @TestConfiguration
    class ControllerTestConfig {
        @Bean
        fun userService(): UserService = mockk(relaxed = true)
    }


    @AfterEach
    fun tearDown() {
        // Aufräumen nach jedem Test
        clearMocks(userService)
    }

    // =========================================================================
    // A. PUBLIC ENDPOINTS (REGISTER & LOGIN)
    // =========================================================================

    @Test
    fun `register should create user and return 200 OK`() {
        val createDto = UserCreateDto("newuser", "new@example.com", "SecurePassword12")

        every { userService.register(any()) } returns UserResponseDto(UUID.randomUUID(), "newuser", "new@example.com")

        mockMvc.perform(post("/api/users/register")
            .with(csrf()) // Benötigt für POST, da Security zwar deaktiviert ist, aber MockMvc den Filter simulieren kann
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createDto)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("newuser"))

        verify(exactly = 1) { userService.register(any()) }
    }

    @Test
    fun `register should return 409 Conflict if user exists`() {
        val createDto = UserCreateDto("exist", "exist@e.com", "SecurePassword12")
        every { userService.register(any()) } throws RequestException(HttpStatus.CONFLICT, "Username already taken")

        mockMvc.perform(post("/api/users/register")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createDto)))
            .andExpect(status().isConflict)
    }

    @Test
    fun `login should return 200 OK and JWT on success`() {
        val responseDto = LoginResponseDto("mocked.jwt.token", LOGIN_DTO.email)
        every { userService.login(any(), any()) } returns responseDto

        mockMvc.perform(post("/api/users/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(LOGIN_DTO)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("mocked.jwt.token"))
    }

    @Test
    fun `login should return 401 Unauthorized on failure`() {
        every { userService.login(any(), any()) } throws RequestException(HttpStatus.UNAUTHORIZED, "Invalid email or password")

        mockMvc.perform(post("/api/users/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(LOGIN_DTO)))
            .andExpect(status().isUnauthorized)
    }

    // =========================================================================
    // B. SECURED GET BY ID (Resource Ownership Check im Service)
    // =========================================================================

    @Test
    @WithMockUser(username = USER_ID_STRING, roles = ["USER"])
    fun `getById should return 200 OK when principal requests own user ID`() {
        // ARRANGE: Wir simulieren, dass der Service das Objekt erfolgreich zurückgibt

        var mockPrincipal = Principal { USER_ID_STRING }
        every { userService.getById(USER_ID_UUID, match { it.name == USER_ID_STRING }) } returns USER_RESPONSE

        // ACT & ASSERT
        mockMvc.perform(get("/api/users/{id}", USER_ID_STRING)
            .contentType(MediaType.APPLICATION_JSON)
            .principal(mockPrincipal))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(USER_ID_STRING))

        verify(exactly = 1) { userService.getById(USER_ID_UUID, any()) }
    }

    @Test
    @WithMockUser(username = USER_ID_STRING, roles = ["USER"])
    fun `getById should return 403 Forbidden when principal requests different user ID`() {
        val otherIdString = ADMIN_ID_STRING

        every { userService.getById(UUID.fromString(otherIdString), match { it.name == USER_ID_STRING }) } throws
                RequestException(HttpStatus.FORBIDDEN, "Forbidden")

        // ACT & ASSERT
        mockMvc.perform(get("/api/users/{id}", otherIdString)
            .contentType(MediaType.APPLICATION_JSON)
            .principal(userMockPrincipal))
            .andExpect(status().isForbidden)

        verify(exactly = 1) { userService.getById(UUID.fromString(otherIdString), any<Principal>()) }
    }

    // =========================================================================
    // C. SECURED UPDATE (Resource Ownership Check im Service)
    // =========================================================================

    @Test
    fun `update should return 200 OK when principal updates own user ID`() {
        val updateDto = UserUpdateDto(username = "new_name")
        val updatedResponse = USER_RESPONSE.copy(username = "new_name")


        // ARRANGE
        every {
            userService.update(
                USER_ID_STRING,
                updateDto,
                match { it.name == USER_ID_STRING }
            )
        } returns updatedResponse

        // ACT & ASSERT
        mockMvc.perform(put("/api/users/{userId}", USER_ID_STRING)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateDto))
            .principal(userMockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("new_name"))

        verify(exactly = 1) {
            userService.update(USER_ID_STRING, updateDto, any())
        }
    }

    @Test
    @WithMockUser(username = USER_ID_STRING, roles = ["USER"])
    fun `update should return 403 Forbidden when principal updates different user ID`() {
        val otherIdString = ADMIN_ID_STRING
        val updateDto = UserUpdateDto(username = "hacker_attempt")

        // Arrange
        every { userService.update(otherIdString, updateDto, match { it.name == USER_ID_STRING }) } throws
                RequestException(HttpStatus.FORBIDDEN, "Forbidden")

        // ACT & ASSERT
        mockMvc.perform(put("/api/users/{userId}", otherIdString)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateDto))
            .principal(userMockPrincipal))
            .andExpect(status().isForbidden)

        verify(exactly = 1) { userService.update(otherIdString, updateDto, any()) }
    }

    // =========================================================================
    // D. SECURED GET ALL (Role-Based Check im Controller)
    // =========================================================================

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `getAll should return 200 OK for ADMIN role (PreAuthorize)`() {
        // ARRANGE
        every { userService.getAll() } returns emptyList()

        // ACT & ASSERT
        mockMvc.perform(get("/api/users")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().json("[]")) // Leere Liste erwartet

        verify(exactly = 1) { userService.getAll() }
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `getAll should return 403 Forbidden for non-ADMIN roles (PreAuthorize)`() {
        // ARRANGE: nothing bcs controller preauth

        // ACT & ASSERT
        mockMvc.perform(get("/api/users")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { userService.getAll() }
    }
}