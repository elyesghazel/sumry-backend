package ch.sumry.core.module.registration

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "invite_codes")
class InviteCode(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "code_hash", nullable = false, unique = true)
    val codeHash: String = "",

    /** The first characters of the code, so an admin can tell codes apart in the list. */
    @Column(name = "code_hint", nullable = false)
    val codeHint: String = "",

    var note: String? = null,

    @Column(name = "created_by")
    val createdBy: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    @Column(name = "used_by")
    var usedBy: UUID? = null,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
) {
    fun status(now: Instant = Instant.now()): String = when {
        usedAt != null -> "used"
        revokedAt != null -> "revoked"
        expiresAt != null && !expiresAt.isAfter(now) -> "expired"
        else -> "active"
    }
}
