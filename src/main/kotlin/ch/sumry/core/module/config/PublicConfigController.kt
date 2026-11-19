package ch.sumry.core.module.config

import ch.sumry.core.module.registration.RegistrationPolicy
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** What a client needs to know before anyone has signed in, e.g. whether to show signup. */
data class PublicConfigDto(
    /** `closed`, `invite` or `open`. */
    val registrationMode: String,
)

@RestController
@RequestMapping("/api/config")
class PublicConfigController(private val registration: RegistrationPolicy) {

    @GetMapping
    fun config(): PublicConfigDto = PublicConfigDto(registrationMode = registration.mode.value)
}
