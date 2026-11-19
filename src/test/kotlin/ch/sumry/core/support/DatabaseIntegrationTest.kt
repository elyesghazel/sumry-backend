package ch.sumry.core.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.*

/**
 * Full application against a real Postgres (Flyway migrations included) and Redis.
 * The containers start once and are shared by every subclass; each test starts from
 * empty tables. Without a reachable Docker daemon these tests are skipped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
abstract class DatabaseIntegrationTest {

    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var jdbc: JdbcTemplate
    @Autowired protected lateinit var redis: StringRedisTemplate

    @BeforeEach
    fun cleanDatabase() {
        val tables = jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
            String::class.java,
        )
        if (tables.isNotEmpty()) jdbc.execute("TRUNCATE ${tables.joinToString()} CASCADE")
        redis.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    /** Inserts a user directly, bypassing the registration rules under test. */
    protected fun createUser(email: String, password: String, role: String = "USER"): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO users (id, email, username, password, role, token_version) VALUES (?, ?, ?, ?, ?, 0)",
            id, email, email.substringBefore('@'), BCryptPasswordEncoder().encode(password), role,
        )
        return id
    }

    protected fun login(email: String, password: String): String {
        val body = mockMvc.perform(
            post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}""")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    protected fun register(json: String, clientIp: String = "203.0.113.1"): ResultActions =
        mockMvc.perform(
            post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", clientIp)
                .content(json)
        )

    protected fun count(sql: String, vararg args: Any): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine")
        private val redisContainer = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            if (!postgres.isRunning) postgres.start()
            if (!redisContainer.isRunning) redisContainer.start()
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", redisContainer::getHost)
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
            // Test-only key, 32 zero bytes.
            registry.add("jwt.secret") { "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" }
        }
    }
}
