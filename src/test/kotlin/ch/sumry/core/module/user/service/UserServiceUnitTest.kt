// ch.sumry.core.module.user.service.UserServiceUnitTest.kt

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.registration.InviteService
import ch.sumry.core.module.registration.RegistrationPolicy
import ch.sumry.core.module.user.dto.auth.UserCreateDto
import ch.sumry.core.module.user.dto.auth.UserUpdateDto
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.module.user.service.UserService
import ch.sumry.core.security.JwtService
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.security.Principal
import java.util.*

class UserServiceUnitTest {

    // --- Mocks der Abhängigkeiten ---
    private val userRepo: UserRepository = mockk(relaxed = true)
    private val jwtService: JwtService = mockk(relaxed = true)
    private val rateLimiter: ch.sumry.core.security.LoginRateLimiter = mockk(relaxed = true)
    private val revocation: ch.sumry.core.security.TokenRevocationService = mockk(relaxed = true)
    private val invites: InviteService = mockk(relaxed = true)
    private lateinit var userService: UserService

    // --- Konstanten für Resource Ownership Tests ---
    private val OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222")

    // --- Objekte, die in @BeforeEach erstellt werden ---
    private lateinit var ownerPrincipal: Principal
    private lateinit var otherPrincipal: Principal
    private lateinit var ownerUserEntity: User

    @BeforeEach
    fun setUp() {
        // 1. Service initialisieren
        userService = UserService(userRepo, jwtService, rateLimiter, revocation, RegistrationPolicy("open", false), invites)

        // 2. Mock-Principal-Objekte initialisieren (Principal.name = UUID)
        ownerPrincipal = mockk<Principal>().also {
            every { it.name } returns OWNER_ID.toString()
        }
        otherPrincipal = mockk<Principal>().also {
            every { it.name } returns OTHER_USER_ID.toString()
        }

        // 3. Mock-User-Entity initialisieren
        ownerUserEntity = User(
            id = OWNER_ID,
            username = "owner",
            email = "owner@test.ch",
            password = "password"
        )
        ownerUserEntity.encodePassword("placeholder") // Passwort muss gehasht sein
    }

    @AfterEach
    fun tearDown() {
        // Wichtig: Aufräumen nach jedem Test
        clearMocks(userRepo, jwtService, answers = false)
    }

    // =========================================================================
    // 1. GET BY ID TESTS (Resource Ownership)
    // =========================================================================

    @Test
    fun `getById should return 200 OK when principal owns the user ID`() {
        // ARRANGE
        every { userRepo.findById(OWNER_ID) } returns Optional.of(ownerUserEntity)

        // ACT
        val response = userService.getById(OWNER_ID, ownerPrincipal)

        // ASSERT
        assertNotNull(response)
        assertEquals(OWNER_ID, response.id)
        verify(exactly = 1) { userRepo.findById(OWNER_ID) }
    }

    @Test
    fun `getById should throw 403 Forbidden when principal requests another user's ID`() {
        // ARRANGE
        // Wir mocken, dass der Benutzer, dessen ID angefragt wird, existiert
        every { userRepo.findById(OTHER_USER_ID) } returns Optional.of(
            User(id = OTHER_USER_ID, username = "other", email = "other@test.ch", password = "password")
        )

        // ACT & ASSERT
        // Der Aufruf erfolgt durch den OWNER, aber er fragt nach OTHER_USER_ID
        val exception = assertThrows<RequestException> {
            userService.getById(OTHER_USER_ID, ownerPrincipal)
        }

        // ASSERT: Die Überprüfung im Service (providedUserId != id) sollte greifen
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
        assertEquals("Forbidden", exception.message)
        // Wichtig: Die findById-DB-Suche sollte NICHT stattfinden, da die 403-Prüfung VORHER kommt.
        verify(exactly = 0) { userRepo.findById(any()) }
    }

    @Test
    fun `getById should throw 404 Not Found if resource does not exist`() {
        // ARRANGE
        every { userRepo.findById(OWNER_ID) } returns Optional.empty()

        // ACT & ASSERT: Die 404-Prüfung sollte nach der 403-Prüfung erfolgen
        val exception = assertThrows<RequestException> {
            userService.getById(OWNER_ID, ownerPrincipal)
        }

        // ASSERT: Die Exception wird korrekt ausgelöst
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        verify(exactly = 1) { userRepo.findById(OWNER_ID) }
    }

    // =========================================================================
    // 2. UPDATE TESTS (Resource Ownership)
    // =========================================================================

    @Test
    fun `update should succeed when principal updates their own user ID`() {
        // ARRANGE
        val updateDto = UserUpdateDto(username = "new_owner_name")
        every { userRepo.findById(OWNER_ID) } returns Optional.of(ownerUserEntity)
        every { userRepo.save(any()) } returns ownerUserEntity.apply { username = "new_owner_name" }

        // ACT
        val response = userService.update(OWNER_ID.toString(), updateDto, ownerPrincipal)

        // ASSERT
        assertEquals("new_owner_name", response.username)
        // Verify, dass die save-Methode mit der neuen Entität aufgerufen wurde
        verify(exactly = 1) { userRepo.save(match { it.username == "new_owner_name" }) }
    }

    @Test
    fun `update should throw 403 Forbidden when principal tries to update another user ID`() {
        // ARRANGE
        val updateDto = UserUpdateDto(username = "hacker")

        // ACT & ASSERT
        // Der OWNER versucht, die OTHER_USER_ID zu aktualisieren
        val exception = assertThrows<RequestException> {
            userService.update(OTHER_USER_ID.toString(), updateDto, ownerPrincipal)
        }

        // ASSERT: Die 403-Prüfung sollte greifen
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
        verify(exactly = 0) { userRepo.findById(any()) } // Es sollte keine DB-Abfrage geben
    }

    // =========================================================================
    // 3. REGISTRATION TESTS (Ohne Principal, da public)
    // =========================================================================

    // (Fügen Sie hier Ihre bestehenden register-Tests ein, um Konflikte zu prüfen)

    @Test
    fun `register should throw CONFLICT if username is taken`() {
        // ARRANGE
        val dto = UserCreateDto("testuser", "new@example.com", "SecurePassword12")
        every { userRepo.existsByUsername(dto.username) } returns true

        // ACT & ASSERT
        val exception = assertThrows<RequestException> { userService.register(dto) }
        assertEquals(HttpStatus.CONFLICT, exception.status)
        assertEquals("Username already taken", exception.message)
    }

    // ... (Fügen Sie hier alle weiteren notwendigen Unit-Tests hinzu, wie z.B. login)
}