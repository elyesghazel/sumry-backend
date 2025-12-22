package ch.sumry.core.module.transaction.controller

import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.transaction.dto.TransactionUpdateDTO
import ch.sumry.core.module.transaction.service.TransactionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*


@RestController
@RequestMapping("/api/transactions")
class TransactionController(private val service: TransactionService) {

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{userId}")
    fun getAllFromUser(@PathVariable userId: String, principal: Principal): List<TransactionResponseDTO> {
        return service.getByUserIdAdmin(UUID.fromString(userId), principal)
    }

    @GetMapping("/")
    fun getAllFromUserPrincipal(principal: Principal): List<TransactionResponseDTO> {
        return service.getByUserId(UUID.fromString(principal.name), principal)
    }



    @PostMapping("/")
    fun createTransaction(@Valid @RequestBody dto: TransactionCreateDTO, principal: Principal): TransactionResponseDTO {
        return service.logTransaction(dto, principal)
    }

    @PutMapping("/{transactionId}")
    fun updateTransaction(@PathVariable transactionId: String, @Valid @RequestBody dto: TransactionUpdateDTO, principal: Principal): TransactionResponseDTO {
        return service.update(UUID.fromString(transactionId), dto, principal)
    }

    @DeleteMapping("/{transactionId}")
    fun deleteTransaction(@PathVariable transactionId: String, principal: Principal): ResponseEntity<Unit> {
        // Implementation for deleting a transaction would go here
        service.delete(UUID.fromString(transactionId), principal)
        return ResponseEntity.noContent().build()
    }

}