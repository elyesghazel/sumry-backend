package ch.sumry.core.module.statement

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.time.YearMonth

@RestController
@RequestMapping("/api/statement")
class StatementController(private val service: StatementService) {

    /** GET /api/statement?month=2026-08 (defaults to last month). */
    @GetMapping
    fun get(@RequestParam(required = false) month: String?, principal: Principal): StatementDTO =
        service.statement(principal, parse(month))

    /** The same statement as the HTML the email carries — handy to preview. */
    @GetMapping("/html", produces = [MediaType.TEXT_HTML_VALUE])
    fun html(@RequestParam(required = false) month: String?, principal: Principal): String =
        service.render(service.statement(principal, parse(month)))

    @GetMapping("/mail-status")
    fun mailStatus(): Map<String, Boolean> = mapOf("configured" to service.mailConfigured)

    @PostMapping("/send")
    fun send(@RequestParam(required = false) month: String?, @RequestParam(required = false) to: String?, principal: Principal): Map<String, String> =
        mapOf("sentTo" to service.send(principal, parse(month), to))

    private fun parse(month: String?): YearMonth = month?.let { YearMonth.parse(it) } ?: YearMonth.now().minusMonths(1)
}
