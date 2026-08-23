package ch.sumry.core.module.user.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.util.*

/**
 * Per-user settings that belong to the account rather than to one installation, so a
 * reinstall or a second device inherits them.
 *
 * Device-specific things deliberately stay out: haptics, which insight cards are shown
 * and the like are properties of the phone in your hand, not of the account. Biometrics
 * are the strongest case of all — the OS never hands over the fingerprint, so only the
 * *intent* to lock is stored here; the check itself can only ever happen on the device.
 */
@Entity
@Table(name = "user_preferences")
class UserPreferences(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    // Nullable in Kotlin, NOT NULL in the schema — the same shape the other entities use.
    // Every constructor parameter needs a default so Kotlin emits the no-argument
    // constructor Hibernate requires to materialise a row.
    // User.preferences points back here, so serialising either side without this
    // recurses until Jackson's depth cap — 119 KB of repeated user rows per request.
    @field:JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    var preferredCurrency: String = "CHF",

    /** "system" | "light" | "dark" */
    var theme: String = "system",

    var language: String = "en",

    /** "off" | "balances" | "all" — how much is masked when someone else can see the screen. */
    var privacy: String = "off",

    /** Require a device unlock (biometric or passcode) before the app can be used. */
    var appLock: Boolean = false,

    /**
     * Seconds the app may sit in the background before it locks again. Zero means lock
     * every time it leaves the foreground.
     */
    var lockDelaySeconds: Int = 60,

    /** Default analytics window: 7d | 30d | month | 3m | 6m | year */
    var timeframe: String = "30d",
) {
    constructor(user: User) : this(id = null, user = user)
}
