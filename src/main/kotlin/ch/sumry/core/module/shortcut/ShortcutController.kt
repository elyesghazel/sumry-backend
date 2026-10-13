package ch.sumry.core.module.shortcut

import ch.sumry.core.exception.RequestException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.*
import java.security.Principal

/**
 * For the Apple Shortcuts automation that logs Apple Pay payments. Reachable with a normal
 * session and with the scoped token from POST /api/users/shortcut-token.
 *
 * Every answer, failures included, carries a `message`: the shortcut shows it as its
 * notification, so a mistake reads as a sentence instead of a conversion error.
 */
@RestController
@RequestMapping("/api/shortcut")
class ShortcutController(private val service: ShortcutService) {

    @GetMapping("/categories")
    fun categories(@RequestParam(required = false) merchant: String?, principal: Principal): ShortcutCategoriesDTO =
        service.categories(principal, merchant)

    @PostMapping("/log")
    fun log(@RequestBody dto: ShortcutLogDTO, principal: Principal): ShortcutLogResultDTO = service.log(principal, dto)

    @ExceptionHandler(RequestException::class)
    fun failed(ex: RequestException) = fail(ex.status, ex.message ?: "Request failed")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable() = fail(HttpStatus.BAD_REQUEST, "Body must be JSON with amount, merchant, card and category")

    private fun fail(status: HttpStatus, message: String) =
        ResponseEntity.status(status).body(mapOf("error" to status.reasonPhrase, "message" to "Sumry: $message"))
}
