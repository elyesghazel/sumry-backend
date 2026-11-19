package ch.sumry.core.module.registration

import ch.sumry.core.module.admin.AdminAction
import ch.sumry.core.module.admin.AdminAuditService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*

/** Invite codes for SUMRY_REGISTRATION_MODE=invite. Admins only. */
@RestController
@RequestMapping("/api/admin/invites")
@PreAuthorize("hasRole('ADMIN')")
class InviteController(
    private val service: InviteService,
    private val audit: AdminAuditService,
) {

    /** The response carries the full code; it is the only time it can be read. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody(required = false) dto: InviteCreateDto?, principal: Principal): InviteResponseDto {
        val adminId = UUID.fromString(principal.name)
        val invite = service.create(dto ?: InviteCreateDto(), adminId)
        audit.record(adminId, AdminAction.INVITE_CREATE, detail = inviteDetail(invite))
        return invite
    }

    @GetMapping
    fun list(): List<InviteResponseDto> = service.list()

    @DeleteMapping("/{id}")
    fun revoke(@PathVariable id: UUID, principal: Principal): InviteResponseDto {
        val invite = service.revoke(id)
        audit.record(UUID.fromString(principal.name), AdminAction.INVITE_REVOKE, detail = inviteDetail(invite))
        return invite
    }

    // Never the code itself, and not the admin's free-text label either.
    private fun inviteDetail(invite: InviteResponseDto) = mapOf(
        "inviteId" to invite.id.toString(),
        "codeHint" to invite.codeHint,
        "expiresAt" to invite.expiresAt?.toString(),
    )
}
