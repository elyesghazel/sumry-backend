package ch.sumry.core.module.transaction.controller

import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.transaction.service.TransactionService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*


@RestController
@RequestMapping("/api/transactions")
class TransactionController(private val service: TransactionService) {

    @GetMapping("/user/{userId}")
    fun getAllFromUser(@PathVariable userId: String, principal: Principal): List<TransactionResponseDTO> {
        return service.getByUserId(UUID.fromString(userId), principal)
    }

    @PostMapping("/")
    fun createTransaction(@Valid @RequestBody dto: TransactionCreateDTO, principal: Principal): TransactionResponseDTO {
        return service.logTransaction(dto, principal)
    }
}