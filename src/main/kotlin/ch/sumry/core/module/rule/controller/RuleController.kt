package ch.sumry.core.module.rule.controller

import ch.sumry.core.module.rule.dto.*
import ch.sumry.core.module.rule.service.RuleService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/rules")
class RuleController(private val service: RuleService) {

    @GetMapping
    fun list(principal: Principal): List<RuleResponseDTO> = service.list(principal)

    @PostMapping
    fun create(@Valid @RequestBody dto: RuleCreateDTO, principal: Principal): ResponseEntity<RuleResponseDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, principal))

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody dto: RuleUpdateDTO, principal: Principal): RuleResponseDTO =
        service.update(id, dto, principal)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, principal: Principal): ResponseEntity<Unit> {
        service.delete(id, principal)
        return ResponseEntity.noContent().build()
    }

    /** Which rule (if any) would fire for a note. */
    @PostMapping("/test")
    fun test(@RequestBody dto: RuleTestDTO, principal: Principal): RuleMatchDTO = service.test(dto, principal)

    /** Recategorise history. `dryRun=true` (the default) only reports what would change. */
    @PostMapping("/apply")
    fun apply(
        @RequestParam(defaultValue = "true") dryRun: Boolean,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        principal: Principal,
    ): RuleApplyResult = service.apply(principal, dryRun, from, to)
}
