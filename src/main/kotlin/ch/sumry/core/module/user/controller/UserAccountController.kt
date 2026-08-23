package ch.sumry.core.module.user.controller

import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.user.dto.accounts.AccountReconcileDto
import ch.sumry.core.module.user.dto.accounts.UserAccountCreateDto
import ch.sumry.core.module.user.dto.accounts.UserAccountResponseDto
import ch.sumry.core.module.user.dto.accounts.UserAccountUpdateDto
import ch.sumry.core.module.user.service.UserAccountService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*


@RestController
@RequestMapping("/api/users/accounts")
class UserAccountController(private val service: UserAccountService) {

    @Tag(name = "User Accounts", description = "Banking Accounts like cash, card, savings")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    fun getAll(): List<UserAccountResponseDto> = service.getAll()


    @Tag(name = "Users", description = "Get all banking accounts from specific User")
    @GetMapping("/user/{userId}")
    fun getFromUser(@PathVariable userId: UUID, principal: Principal): List<UserAccountResponseDto> = service.getAllFromUser(userId, principal)

    @GetMapping("/")
    fun getFromPrincipal(principal: Principal): List<UserAccountResponseDto> = service.getAllFromPrincipal(principal)
    @Tag(name = "User Accounts", description = "Get specific banking account by ID")
    @GetMapping("/id/{accountId}")
    fun getById(@PathVariable accountId: UUID, principal: Principal): UserAccountResponseDto =
        service.findById(accountId, principal)

    @PostMapping
    fun create(@Valid @RequestBody dto: UserAccountCreateDto, principal: Principal): ResponseEntity<UserAccountResponseDto> {
        val response = service.create(dto, principal)
        return ResponseEntity.status(201).body(response)
    }

    @PutMapping("/balance/{accountId}")
    fun updateBalance(@PathVariable accountId: UUID, @Valid @RequestBody dto: UserAccountUpdateDto, principal: Principal): UserAccountResponseDto =
        service.updateBalance(accountId, dto, principal)

    @PutMapping("/{accountId}")
    fun update(@PathVariable accountId: UUID, @Valid @RequestBody dto: UserAccountUpdateDto, principal: Principal): UserAccountResponseDto =
        service.update(accountId, dto, principal)

    @Tag(name = "User Accounts", description = "Set the real balance; the difference is booked as a transaction")
    @PutMapping("/reconcile/{accountId}")
    fun reconcile(
        @PathVariable accountId: UUID,
        @Valid @RequestBody dto: AccountReconcileDto,
        principal: Principal
    ): ResponseEntity<TransactionResponseDTO> {
        val adjustment = service.reconcile(accountId, dto, principal)
        // 204 when the account already matched and nothing needed booking.
        return adjustment?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{accountId}")
    fun delete(@PathVariable accountId: UUID, principal: Principal): ResponseEntity<Unit> {
        service.delete(accountId, principal)
        return ResponseEntity.noContent().build()
    }
}