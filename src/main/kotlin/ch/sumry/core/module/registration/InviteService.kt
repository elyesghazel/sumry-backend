package ch.sumry.core.module.registration

import ch.sumry.core.exception.RequestException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*

@Service
class InviteService(private val repo: InviteCodeRepository) {

    private val random = SecureRandom()

    @Transactional
    fun create(dto: InviteCreateDto, adminId: UUID): InviteResponseDto {
        val code = newCode()
        val invite = repo.save(
            InviteCode(
                codeHash = hash(code),
                codeHint = code.take(HINT_LENGTH),
                note = dto.note?.trim()?.takeIf { it.isNotEmpty() },
                createdBy = adminId,
                expiresAt = dto.expiresInDays?.let { Instant.now().plus(Duration.ofDays(it.toLong())) },
            )
        )
        return toDto(invite, code)
    }

    fun list(): List<InviteResponseDto> = repo.findAllByOrderByCreatedAtDesc().map { toDto(it, null) }

    /** Revoking a code that was already used changes nothing; the account it created stays. */
    @Transactional
    fun revoke(id: UUID): InviteResponseDto {
        val invite = repo.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Invite not found") }
        if (invite.usedAt == null && invite.revokedAt == null) {
            invite.revokedAt = Instant.now()
            repo.save(invite)
        }
        return toDto(invite, null)
    }

    fun isRedeemable(code: String): Boolean = repo.isRedeemable(hash(normalise(code)), Instant.now())

    /** Must run inside the transaction that creates the user, so a failed redeem rolls it back. */
    fun redeem(code: String, userId: UUID): Boolean = repo.redeem(hash(normalise(code)), userId, Instant.now()) == 1

    private fun newCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) { sb.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }

    private fun toDto(invite: InviteCode, code: String?) = InviteResponseDto(
        id = invite.id,
        code = code,
        codeHint = invite.codeHint,
        note = invite.note,
        status = invite.status(),
        createdAt = invite.createdAt,
        expiresAt = invite.expiresAt,
        usedAt = invite.usedAt,
        revokedAt = invite.revokedAt,
    )

    companion object {
        // No 0/O, 1/I/L, so a code read off a screen or typed on a phone survives.
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 16
        private const val HINT_LENGTH = 4

        /** Codes are case-insensitive and may be pasted with spaces or dashes. */
        fun normalise(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

        fun hash(code: String): String =
            MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
