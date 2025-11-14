package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.accounts.UserAccountCreateDto
import ch.sumry.core.module.user.dto.accounts.UserAccountResponseDto
import ch.sumry.core.module.user.dto.accounts.UserAccountUpdateDto
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.service.UserAccountService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*


@RestController
@RequestMapping("/api/users/accounts")
class UserAccountController(private val service: UserAccountService) {

    @GetMapping
    fun getAll(): List<UserAccount> = service.getAll()

    @GetMapping("/user/{userId}")
    fun getFromUser(@PathVariable userId: UUID): List<UserAccount> = service.getAllFromUser(userId)

    @GetMapping("/id/{accountId}")
    fun getById(@PathVariable accountId: UUID): UserAccountResponseDto =
        service.findById(accountId)

    @PostMapping
    fun create(@Valid @RequestBody dto: UserAccountCreateDto): UserAccountResponseDto =
        service.create(dto)

    @PutMapping("/{accountId}")
    fun update(@PathVariable accountId: UUID, @Valid @RequestBody dto: UserAccountUpdateDto): UserAccountResponseDto =
        service.updateBalance(accountId, dto)

    @DeleteMapping("/{accountId}")
    fun delete(@PathVariable accountId: UUID): ResponseEntity<Unit> {
        service.delete(accountId)
        return ResponseEntity.noContent().build()
    }
}