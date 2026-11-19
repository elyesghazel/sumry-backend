package ch.sumry.core.module.porting

import ch.sumry.core.ai.AiAccess
import ch.sumry.core.ai.CapabilitiesController
import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.types.TransactionKind
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.security.Principal
import java.time.LocalDate
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/** The allowlist is enforced on the server: a caller off the list never reaches the bridge. */
class ImportCheckerAccessTest {

    private val owner = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val stranger = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val accountId = UUID.randomUUID()

    private val bridgeCalls = AtomicInteger()
    private val bridge = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/check") { ex ->
            bridgeCalls.incrementAndGet()
            val body = """{"result":{"rows":[],"adjustments":[],"summary":"ok"}}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        start()
    }

    private val users = mockk<UserRepository>().also {
        every { it.findById(any<UUID>()) } returns Optional.empty()
    }
    private val accounts = mockk<UserAccountRepository>().also {
        val user = User(owner, "owner", "owner@example.com", "x")
        every { it.findById(accountId) } returns Optional.of(UserAccount(id = accountId, accountName = "PostFinance", currentBalance = BigDecimal.ZERO, user = user))
    }
    private val transactions = mockk<TransactionRepository>().also {
        every { it.findByUserAccount_User_Id(any()) } returns emptyList()
    }
    private val categories = mockk<CategoryRepository>().also {
        every { it.findAll() } returns emptyList()
    }

    private val request = ImportCheckRequest(
        accountId = accountId,
        rows = listOf(ImportCheckRow(1, LocalDate.of(2026, 11, 2), null, "MIGROS", BigDecimal("12.50"), TransactionKind.EXPENSE)),
    )

    private fun checker(allowlist: String, url: String = "http://127.0.0.1:${bridge.address.port}") = ImportChecker(
        url, "token", accounts, transactions, categories, ObjectMapper(), AiAccess(allowlist, users),
    )

    private fun principal(id: UUID) = Principal { id.toString() }

    @AfterEach
    fun stop() = bridge.stop(0)

    @Test
    fun `allowed user reaches the bridge`() {
        val result = checker(owner.toString()).check(request, principal(owner))
        assertEquals("ok", result.summary)
        assertEquals(1, bridgeCalls.get())
        assertTrue(checker(owner.toString()).enabledFor(owner))
    }

    @Test
    fun `user off the list gets 403 and the bridge is never called`() {
        val e = assertThrows<RequestException> { checker(owner.toString()).check(request, principal(stranger)) }
        assertEquals(HttpStatus.FORBIDDEN, e.status)
        assertEquals(0, bridgeCalls.get())
        verify(exactly = 0) { accounts.findById(any<UUID>()) }
        assertFalse(checker(owner.toString()).enabledFor(stranger))
    }

    @Test
    fun `empty list refuses everyone, also the account owner`() {
        val e = assertThrows<RequestException> { checker("").check(request, principal(owner)) }
        assertEquals(HttpStatus.FORBIDDEN, e.status)
        assertEquals(0, bridgeCalls.get())
        assertFalse(checker("").enabledFor(owner))
    }

    @Test
    fun `allowed but no bridge configured is still off`() {
        val c = checker(owner.toString(), url = "")
        assertFalse(c.enabledFor(owner))
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows<RequestException> { c.check(request, principal(owner)) }.status)
    }

    @Test
    fun `capabilities report aiEnabled per user`() {
        val controller = CapabilitiesController(checker(owner.toString()))
        assertTrue(controller.get(principal(owner)).aiEnabled)
        assertFalse(controller.get(principal(stranger)).aiEnabled)
    }
}
