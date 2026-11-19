package ch.sumry.core.module.admin

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*

/** Admin user management. Role ADMIN on every route; no impersonation route, on purpose. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val service: AdminUserService,
    private val audit: AdminAuditService,
) {

    @GetMapping("/users")
    fun users(): List<AdminUserDto> = service.list()

    @GetMapping("/stats")
    fun stats(): AdminStatsDto = service.stats()

    @GetMapping("/audit")
    fun audit(@RequestParam(required = false) page: Int?, @RequestParam(required = false) size: Int?): AdminAuditPageDto =
        audit.page(page, size)

    @PostMapping("/users/{id}/lock")
    fun lock(@PathVariable id: UUID, principal: Principal): AdminUserDto = service.lock(adminId(principal), id)

    @PostMapping("/users/{id}/unlock")
    fun unlock(@PathVariable id: UUID, principal: Principal): AdminUserDto = service.unlock(adminId(principal), id)

    @DeleteMapping("/users/{id}")
    fun delete(@PathVariable id: UUID, principal: Principal): ResponseEntity<Unit> {
        service.delete(adminId(principal), id)
        return ResponseEntity.noContent().build()
    }

    /** The token is in this response only; the user redeems it at POST /api/users/password-reset. */
    @PostMapping("/users/{id}/password-reset")
    fun passwordReset(@PathVariable id: UUID, principal: Principal): PasswordResetTokenDto =
        service.createPasswordReset(adminId(principal), id)

    private fun adminId(principal: Principal): UUID = UUID.fromString(principal.name)
}
