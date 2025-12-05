// ch.sumry.core.module.user.service.UserServiceUnitTest.kt

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.auth.LoginDto
import ch.sumry.core.module.user.dto.auth.UserCreateDto
import ch.sumry.core.module.user.dto.auth.UserUpdateDto
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.module.user.service.UserService
import ch.sumry.core.security.JwtService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.util.*

class UserServiceUnitTest {

    private val userRepo: UserRepository = mockk()
    private val jwtService: JwtService = mockk()
    private lateinit var userService: UserService

    // Test Data
    private val TEST_USER_ID = UUID.randomUUID()
    private val RAW_PASSWORD = "Password123"
    private lateinit var hashedPassword: String
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        userService = UserService(userRepo, jwtService)
        // 🚨 IMPORTANT: Manually encode the password to mock what the DB holds
        hashedPassword = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(RAW_PASSWORD)

        testUser = User(
            id = TEST_USER_ID,
            username = "testuser",
            email = "test@example.com",
            password = hashedPassword // Store the hashed version
        )
    }

    // -------------------------------------------------------------------------
    // A. REGISTER TESTS
    // -------------------------------------------------------------------------

    @Test
    fun `register should succeed when user is new`() {
        // ARRANGE
        val dto = UserCreateDto("newuser", "new@example.com", RAW_PASSWORD)

        // Mock DB checks to return false
        every { userRepo.existsByUsername(dto.username) } returns false
        every { userRepo.existsByEmail(dto.email) } returns false

        // Mock save operation (capture the argument for verification)
        every { userRepo.save(any()) } returnsArgument 0 // Returns the user object it was given

        // ACT
        val response = userService.register(dto)

        // ASSERT
        assertEquals("newuser", response.username)
        assertEquals("new@example.com", response.email)

        // Verify save was called and the password was encoded
        verify(exactly = 1) { userRepo.save(match { user -> user.password != RAW_PASSWORD && user.email == "new@example.com" }) }
    }

    @Test
    fun `register should throw CONFLICT if username is taken`() {
        // ARRANGE
        val dto = UserCreateDto("testuser", "new@example.com", RAW_PASSWORD)
        every { userRepo.existsByUsername(dto.username) } returns true

        // ACT & ASSERT
        val exception = assertThrows<RequestException> { userService.register(dto) }
        assertEquals(HttpStatus.CONFLICT, exception.status)
    }

    // -------------------------------------------------------------------------
    // B. LOGIN TESTS
    // -------------------------------------------------------------------------

    @Test
    fun `login should succeed and return JWT on valid credentials`() {
        // ARRANGE
        val loginDto = LoginDto(testUser.email, RAW_PASSWORD)
        val expectedToken = "mocked.jwt.token"

        // Mock DB lookup
        every { userRepo.findByEmail(loginDto.email) } returns testUser

        // Mock JWT generation
        every { jwtService.generateToken(any(), any()) } returns expectedToken

        // ACT
        val response = userService.login(loginDto)

        // ASSERT
        assertEquals(expectedToken, response.token)
        assertEquals(testUser.email, response.email)

        // Verify JWT service was called with correct claims (UUID subject)
        verify(exactly = 1) {
            jwtService.generateToken(
                subject = TEST_USER_ID.toString(),
                extraClaims = match { map -> map["email"] == testUser.email && map["username"] == testUser.username }
            )
        }
    }

    @Test
    fun `login should throw UNAUTHORIZED on invalid password`() {
        // ARRANGE
        val loginDto = LoginDto(testUser.email, "WrongPassword")
        every { userRepo.findByEmail(loginDto.email) } returns testUser // Email found

        // ACT & ASSERT
        val exception = assertThrows<RequestException> { userService.login(loginDto) }
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        verify(exactly = 0) { jwtService.generateToken(any(), any()) }
    }

    @Test
    fun `login should throw UNAUTHORIZED on non-existent email`() {
        // ARRANGE
        val loginDto = LoginDto("nonexistent@example.com", RAW_PASSWORD)
        every { userRepo.findByEmail(loginDto.email) } returns null // Email not found

        // ACT & ASSERT
        val exception = assertThrows<RequestException> { userService.login(loginDto) }
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    // -------------------------------------------------------------------------
    // C. UPDATE TESTS
    // -------------------------------------------------------------------------

    @Test
    fun `update should change username and email`() {
        // ARRANGE
        val newUsername = "updated_user"
        val newEmail = "updated@example.com"
        val updateDto = UserUpdateDto(newUsername, newEmail)

        every { userRepo.findById(TEST_USER_ID) } returns Optional.of(testUser)
        every { userRepo.save(any()) } returnsArgument 0

        // ACT
        val response = userService.update(TEST_USER_ID.toString(), updateDto)

        // ASSERT
        assertEquals(newUsername, response.username)
        assertEquals(newEmail, response.email)

        // Verify save was called with the updated user
        verify(exactly = 1) { userRepo.save(match { user -> user.username == newUsername && user.email == newEmail }) }
    }

    // -------------------------------------------------------------------------
    // D. GET BY ID TESTS
    // -------------------------------------------------------------------------

    @Test
    fun `getById should return user dto when user exists`() {
        // ARRANGE
        every { userRepo.findById(TEST_USER_ID) } returns Optional.of(testUser)

        // ACT
        val response = userService.getById(TEST_USER_ID)

        // ASSERT
        assertEquals(testUser.username, response.username)
    }

    @Test
    fun `getById should throw NOT_FOUND when user does not exist`() {
        // ARRANGE
        every { userRepo.findById(any()) } returns Optional.empty()

        // ACT & ASSERT
        val exception = assertThrows<RequestException> { userService.getById(UUID.randomUUID()) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}