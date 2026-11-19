package ch.sumry.core.module.plan

import ch.sumry.core.module.porting.ImportChecker
import ch.sumry.core.module.porting.PortingController
import ch.sumry.core.module.porting.PortingService
import ch.sumry.core.module.user.model.Plan
import ch.sumry.core.module.user.model.User
import ch.sumry.core.security.MethodSecurityTestConfig
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
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.*

private const val USER_ID = "22222222-2222-2222-2222-222222222222"

@WebMvcTest(
    controllers = [AdminPlanController::class, PortingController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
)
@Import(MethodSecurityTestConfig::class)
class PlanControllersTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var plans: PlanService
    @Autowired private lateinit var porting: PortingService

    @TestConfiguration
    class Config {
        @Bean fun planService(): PlanService = mockk(relaxed = true)
        @Bean fun portingService(): PortingService = mockk(relaxed = true)
        @Bean fun importChecker(): ImportChecker = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() = clearMocks(plans, porting)

    @Test
    @WithMockUser(username = USER_ID, roles = ["ADMIN"])
    fun `admin sets a user's plan`() {
        val id = UUID.fromString(USER_ID)
        val user = User(id = id, username = "u", email = "u@test.ch", password = "x", plan = Plan.PREMIUM)
        every { plans.setPlan(id, Plan.PREMIUM) } returns user
        every { plans.effective(user) } returns EffectivePlan(Plan.PREMIUM, true, PlanLimits.UNLIMITED)

        mockMvc.perform(
            post("/api/admin/users/{id}/plan", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"plan":"PREMIUM"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plan").value("PREMIUM"))
            .andExpect(jsonPath("$.effectivePlan").value("PREMIUM"))
            .andExpect(jsonPath("$.limits.statementImport").value(true))
    }

    @Test
    @WithMockUser(username = USER_ID, roles = ["USER"])
    fun `non-admin cannot set a plan`() {
        mockMvc.perform(
            post("/api/admin/users/{id}/plan", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"plan":"PREMIUM"}""")
        ).andExpect(status().isForbidden)

        verify(exactly = 0) { plans.setPlan(any(), any()) }
    }

    @Test
    @WithMockUser(username = USER_ID, roles = ["ADMIN"])
    fun `setting a plan without a plan is a bad request`() {
        mockMvc.perform(
            post("/api/admin/users/{id}/plan", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = USER_ID, roles = ["USER"])
    fun `blocked import answers 403 plan_limit and does not run`() {
        every { plans.requireStatementImport(UUID.fromString(USER_ID)) } throws PlanLimitException(PlanLimit.STATEMENT_IMPORT)

        mockMvc.perform(
            post("/api/import/preview")
                .principal(Principal { USER_ID })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"accountId":"$USER_ID","format":"auto","csv":""}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("plan_limit"))
            .andExpect(jsonPath("$.limit").value("statement_import"))

        verify(exactly = 0) { porting.preview(any(), any()) }
    }
}
