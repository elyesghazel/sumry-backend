package ch.sumry.core.module.porting

import ch.sumry.core.module.plan.PlanService
import ch.sumry.core.module.transaction.mapper.TransactionMapper
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api")
class PortingController(
    private val service: PortingService,
    private val checker: ImportChecker,
    private val plans: PlanService,
) {

    /** Parse a statement and see what would be booked, what is already there, and what lacks a category. */
    @PostMapping("/import/preview")
    fun preview(@RequestBody req: ImportPreviewRequest, principal: Principal): ImportPreviewDTO {
        plans.requireStatementImport(UUID.fromString(principal.name))
        return service.preview(req, principal)
    }

    /** Ask Claude about the rows the preview could not match; suggestions only, nothing is written. */
    @PostMapping("/import/check")
    fun check(@RequestBody req: ImportCheckRequest, principal: Principal): ImportCheckDTO {
        plans.requireStatementImport(UUID.fromString(principal.name))
        return checker.check(req, principal)
    }

    @PostMapping("/import/commit")
    fun commit(@RequestBody req: ImportCommitRequest, principal: Principal): ImportCommitDTO {
        plans.requireStatementImport(UUID.fromString(principal.name))
        return service.commit(req, principal)
    }

    @GetMapping("/export/transactions.csv", produces = ["text/csv"])
    fun exportCsv(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) accountId: UUID?,
        principal: Principal,
    ): ResponseEntity<ByteArray> {
        val csv = service.toCsv(service.exportRows(principal, from, to, accountId))
        val name = "sumry-transactions-${LocalDate.now()}.csv"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$name\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
            // A BOM makes Excel open the file as UTF-8 instead of mangling the umlauts.
            .body(("﻿" + csv).toByteArray(StandardCharsets.UTF_8))
    }

    @GetMapping("/export/transactions.json")
    fun exportJson(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) accountId: UUID?,
        principal: Principal,
    ) = TransactionMapper.toDtoList(service.exportRows(principal, from, to, accountId))
}
