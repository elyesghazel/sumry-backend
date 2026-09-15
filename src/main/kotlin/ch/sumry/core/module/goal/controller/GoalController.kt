package ch.sumry.core.module.goal.controller

import ch.sumry.core.module.goal.dto.*
import ch.sumry.core.module.goal.service.GoalService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*

@RestController
@RequestMapping("/api/goals")
class GoalController(private val service: GoalService) {

    @GetMapping
    fun list(@RequestParam(defaultValue = "false") includeInactive: Boolean, principal: Principal): List<GoalResponseDTO> =
        service.list(principal, includeInactive)

    @PostMapping
    fun create(@Valid @RequestBody dto: GoalCreateDTO, principal: Principal): ResponseEntity<GoalResponseDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, principal))

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody dto: GoalUpdateDTO, principal: Principal): GoalResponseDTO =
        service.update(id, dto, principal)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, principal: Principal): ResponseEntity<Unit> {
        service.delete(id, principal)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/contribute")
    fun contribute(@PathVariable id: UUID, @Valid @RequestBody dto: GoalContributeDTO, principal: Principal): GoalResponseDTO =
        service.contribute(id, dto, principal)
}
