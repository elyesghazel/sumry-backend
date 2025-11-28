package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.accounts.UserAccountCreateDto
import ch.sumry.core.module.user.dto.accounts.UserAccountResponseDto
import ch.sumry.core.module.user.dto.accounts.UserAccountUpdateDto
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.service.UserAccountService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*


@RestController
@RequestMapping("/api/users/accounts")
class UserAccountController(private val service: UserAccountService) {

    @Tag(name = "User Accounts", description = "Banking Accounts like cash, card, savings")
    @GetMapping
    fun getAll(): List<UserAccount> = service.getAll()

    @Tag(name = "Users", description = "Get all banking accounts from specific User")
    @GetMapping("/user/{userId}")
    fun getFromUser(@PathVariable userId: UUID): List<UserAccount> = service.getAllFromUser(userId)

    @Tag(name = "User Accounts", description = "Get specific banking account by ID")
    @GetMapping("/id/{accountId}")
    fun getById(@PathVariable accountId: UUID): UserAccountResponseDto =
        service.findById(accountId)

    @PostMapping
    fun create(@Valid @RequestBody dto: UserAccountCreateDto): ResponseEntity<Unit> {
        val response = service.create(dto)
        return ResponseEntity.created(null).body(response).build()
    }

    @PutMapping("/balance/{accountId}")
    fun updateBalance(@PathVariable accountId: UUID, @Valid @RequestBody dto: UserAccountUpdateDto): UserAccountResponseDto =
        service.updateBalance(accountId, dto)

    @PatchMapping("/{accountId}")
    fun update(@PathVariable accountId: UUID, @Valid @RequestBody dto: UserAccountUpdateDto): UserAccountResponseDto =
        service.update(accountId, dto)

    @DeleteMapping("/{accountId}")
    fun delete(@PathVariable accountId: UUID): ResponseEntity<Unit> {
        service.delete(accountId)
        return ResponseEntity.noContent().build()
    }
}