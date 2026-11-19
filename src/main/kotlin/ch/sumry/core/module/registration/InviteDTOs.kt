package ch.sumry.core.module.registration

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.*

data class InviteCreateDto(
    /** Days until the code stops working; null for a code that never expires. */
    @field:Min(1) @field:Max(365)
    val expiresInDays: Int? = null,
    @field:Size(max = 255)
    val note: String? = null,
)

data class InviteResponseDto(
    val id: UUID,
    /** The full code. Only set in the response to creating it; it is not stored. */
    val code: String?,
    val codeHint: String,
    val note: String?,
    /** `active`, `used`, `revoked` or `expired`. */
    val status: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val usedAt: Instant?,
    val revokedAt: Instant?,
)
