package ch.sumry.core.module.registration

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
class InviteController(private val service: InviteService) {

    /** The response carries the full code; it is the only time it can be read. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody(required = false) dto: InviteCreateDto?, principal: Principal): InviteResponseDto =
        service.create(dto ?: InviteCreateDto(), UUID.fromString(principal.name))

    @GetMapping
    fun list(): List<InviteResponseDto> = service.list()

    @DeleteMapping("/{id}")
    fun revoke(@PathVariable id: UUID): InviteResponseDto = service.revoke(id)
}
