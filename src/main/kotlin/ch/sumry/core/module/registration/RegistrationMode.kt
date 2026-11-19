package ch.sumry.core.module.registration

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Who may create an account on this instance. */
enum class RegistrationMode {
    /** Nobody. The default: a self-hosted instance is personal and internet-facing. */
    CLOSED,

    /** Only someone holding an unused invite code from an admin. */
    INVITE,

    /** Anyone. */
    OPEN;

    /** The value as it is written in SUMRY_REGISTRATION_MODE and returned by /api/config. */
    val value: String get() = name.lowercase()

    companion object {
        fun parse(raw: String): RegistrationMode =
            entries.firstOrNull { it.value == raw.trim().lowercase() }
                ?: throw IllegalStateException(
                    "sumry.registration.mode (SUMRY_REGISTRATION_MODE) must be closed, invite or open; got '$raw'"
                )
    }
}

/**
 * Resolves the registration mode once at startup.
 *
 * `sumry.registration.mode` wins when set. Without it the older boolean
 * `sumry.registration.enabled` (SUMRY_REGISTRATION_ENABLED) still works: true means open,
 * false or unset means closed, so existing deployments behave exactly as before.
 */
@Component
class RegistrationPolicy(
    @Value("\${sumry.registration.mode:}") rawMode: String,
    @Value("\${sumry.registration.enabled:false}") legacyEnabled: Boolean,
) {
    val mode: RegistrationMode = when {
        rawMode.isNotBlank() -> RegistrationMode.parse(rawMode)
        legacyEnabled -> RegistrationMode.OPEN
        else -> RegistrationMode.CLOSED
    }
}
