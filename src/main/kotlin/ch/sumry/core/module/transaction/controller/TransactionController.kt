package ch.sumry.core.module.transaction.controller

import ch.sumry.core.module.transaction.dto.CashflowDTO
import ch.sumry.core.module.transaction.dto.TransactionPageDTO
import ch.sumry.core.module.transaction.dto.QuickEntryDTO
import ch.sumry.core.module.transaction.dto.QuickEntryRequest
import ch.sumry.core.module.transaction.dto.TransactionSearchQuery
import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.transaction.dto.TransactionUpdateDTO
import ch.sumry.core.module.transaction.dto.TransferCreateDTO
import ch.sumry.core.module.transaction.dto.TransferResponseDTO
import ch.sumry.core.module.transaction.dto.TransferUpdateDTO
import ch.sumry.core.module.transaction.service.TransactionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.format.annotation.DateTimeFormat
import java.math.BigDecimal
import java.security.Principal
import java.time.LocalDate
import java.util.*


@RestController
@RequestMapping("/api/transactions")
class TransactionController(private val service: TransactionService) {

    @GetMapping("/")
    fun getAllFromUserPrincipal(principal: Principal): List<TransactionResponseDTO> {
        return service.getByUserId(UUID.fromString(principal.name), principal)
    }



    /** GET /api/transactions/search?q=&accountId=&categoryLabel=&kind=&from=&to=&min=&max=&page=&size=&sort=&dir= */
    @GetMapping("/search")
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) accountId: UUID?,
        @RequestParam(required = false) categoryLabel: String?,
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) min: BigDecimal?,
        @RequestParam(required = false) max: BigDecimal?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "date") sort: String,
        @RequestParam(defaultValue = "desc") dir: String,
        principal: Principal,
    ): TransactionPageDTO = service.search(
        TransactionSearchQuery(q, accountId, categoryLabel, kind, from, to, min, max, page, size, sort, dir),
        principal,
    )

    /** Natural-language entry: parse, and book when `book` is true and nothing is missing. */
    @PostMapping("/quick")
    fun quick(@RequestBody req: QuickEntryRequest, principal: Principal): QuickEntryDTO = service.quick(req, principal)

    @PostMapping("/")
    fun createTransaction(@Valid @RequestBody dto: TransactionCreateDTO, principal: Principal): TransactionResponseDTO {
        return service.logTransaction(dto, principal)
    }

    @PutMapping("/{transactionId}")
    fun updateTransaction(@PathVariable transactionId: String, @Valid @RequestBody dto: TransactionUpdateDTO, principal: Principal): TransactionResponseDTO {
        return service.update(UUID.fromString(transactionId), dto, principal)
    }

    @PostMapping("/transfer")
    fun createTransfer(@Valid @RequestBody dto: TransferCreateDTO, principal: Principal): ResponseEntity<TransferResponseDTO> {
        return ResponseEntity.status(201).body(service.createTransfer(dto, principal))
    }

    @PutMapping("/transfer/{transferGroupId}")
    fun updateTransfer(
        @PathVariable transferGroupId: UUID,
        @Valid @RequestBody dto: TransferUpdateDTO,
        principal: Principal
    ): TransferResponseDTO {
        return service.updateTransfer(transferGroupId, dto, principal)
    }

    @DeleteMapping("/transfer/{transferGroupId}")
    fun deleteTransfer(@PathVariable transferGroupId: UUID, principal: Principal): ResponseEntity<Unit> {
        service.deleteTransfer(transferGroupId, principal)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/cashflow/{userAccountIdStr}")
    fun getCashflow(@PathVariable userAccountIdStr: String, principal: Principal): CashflowDTO {
        return service.getIncomeAndExpenseMonth(userAccountIdStr, principal)
    }

    @DeleteMapping("/{transactionId}")
    fun deleteTransaction(@PathVariable transactionId: String, principal: Principal): ResponseEntity<Unit> {
        // Implementation for deleting a transaction would go here
        service.delete(UUID.fromString(transactionId), principal)
        return ResponseEntity.noContent().build()
    }

}