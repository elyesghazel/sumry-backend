package ch.sumry.core.ai

import ch.sumry.core.module.porting.ImportChecker
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.*

data class CapabilitiesDTO(
    /** True when this user is on SUMRY_AI_USERS and the Claude bridge is configured. */
    val aiEnabled: Boolean,
)

@RestController
class CapabilitiesController(private val checker: ImportChecker) {

    @GetMapping("/api/users/me/capabilities")
    fun get(principal: Principal): CapabilitiesDTO = CapabilitiesDTO(aiEnabled = checker.enabledFor(UUID.fromString(principal.name)))
}
