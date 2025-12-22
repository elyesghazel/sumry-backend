package ch.sumry.core.module.budget.controller

import ch.sumry.core.module.budget.dto.BudgetCreateDTO
import ch.sumry.core.module.budget.dto.BudgetProgressDTO
import ch.sumry.core.module.budget.dto.BudgetResponseDTO
import ch.sumry.core.module.budget.dto.BudgetUpdateDTO
import ch.sumry.core.module.budget.service.BudgetService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*

@RestController
@RequestMapping("/api/budgets")
class BudgetController(private val budgetService: BudgetService) {

    // 1. Neues Budget erstellen
    @PostMapping
    fun create(
        @Valid @RequestBody dto: BudgetCreateDTO,
        principal: Principal
    ): ResponseEntity<BudgetResponseDTO> {
        val createdBudget = budgetService.create(dto, principal)
        return ResponseEntity(createdBudget, HttpStatus.CREATED)
    }

    // GET /api/budgets/progress?month=12&year=2025
    @GetMapping("/progress")
    fun getProgress(
        @RequestParam month: Int,
        @RequestParam year: Int,
        principal: Principal
    ): ResponseEntity<List<BudgetProgressDTO>> {
        val userId = UUID.fromString(principal.name)
        val progressList = budgetService.getBudgetProgress(userId, month, year)
        return ResponseEntity.ok(progressList)
    }

    @GetMapping
    fun getAll(principal: Principal): ResponseEntity<List<BudgetResponseDTO>> {
        return ResponseEntity.ok(budgetService.getAllFromPrincipal(principal))
    }

    // 4. Budget ändern (z.B. Limit erhöhen)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: BudgetUpdateDTO,
        principal: Principal
    ): ResponseEntity<BudgetResponseDTO> {
        val updatedBudget = budgetService.update(id, dto, principal)
        return ResponseEntity.ok(updatedBudget)
    }



    // 5. Budget löschen
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, principal: Principal): ResponseEntity<Unit> {
        budgetService.delete(id, principal)
        return ResponseEntity.noContent().build()
    }
}