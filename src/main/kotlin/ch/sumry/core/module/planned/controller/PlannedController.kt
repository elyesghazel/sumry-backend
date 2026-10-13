package ch.sumry.core.module.planned.controller

import ch.sumry.core.module.planned.dto.*
import ch.sumry.core.module.planned.service.PlannedService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/planned")
class PlannedController(private val service: PlannedService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        principal: Principal,
    ): List<PlannedResponseDTO> = service.list(principal, includeInactive)

    @PostMapping
    fun create(@Valid @RequestBody dto: PlannedCreateDTO, principal: Principal): ResponseEntity<PlannedResponseDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, principal))

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody dto: PlannedUpdateDTO, principal: Principal): PlannedResponseDTO =
        service.update(id, dto, principal)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, principal: Principal): ResponseEntity<Unit> {
        service.delete(id, principal)
        return ResponseEntity.noContent().build()
    }

    /** Book the plan's next due occurrence now; `date` overrides the booking date. */
    @PostMapping("/{id}/book")
    fun book(
        @PathVariable id: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        principal: Principal,
    ): PlannedResponseDTO = service.bookNext(id, principal, date)

    /** Let the next occurrence pass unbooked; `date` must match it when given. */
    @PostMapping("/{id}/skip")
    fun skip(
        @PathVariable id: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        principal: Principal,
    ): PlannedResponseDTO = service.skipNext(id, principal, date)

    /** Move the next occurrence to `to`; returns the one-off plan that now carries it. */
    @PostMapping("/{id}/postpone")
    fun postpone(
        @PathVariable id: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        principal: Principal,
    ): PlannedResponseDTO = service.postponeNext(id, principal, date, to)

    @GetMapping("/upcoming")
    fun upcoming(
        @RequestParam(defaultValue = "30") days: Int,
        @RequestParam(required = false) accountId: UUID?,
        principal: Principal,
    ): List<OccurrenceDTO> = service.upcoming(principal, days.coerceIn(1, 366), accountId)

    @GetMapping("/safe-to-spend")
    fun safeToSpend(@RequestParam(required = false) accountId: UUID?, principal: Principal): SafeToSpendDTO =
        service.safeToSpend(principal, accountId)
}
