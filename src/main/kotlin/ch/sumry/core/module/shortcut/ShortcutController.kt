package ch.sumry.core.module.shortcut

import org.springframework.web.bind.annotation.*
import java.security.Principal

/**
 * For the Apple Shortcuts automation that logs Apple Pay payments. Reachable with a normal
 * session and with the scoped token from POST /api/users/shortcut-token.
 */
@RestController
@RequestMapping("/api/shortcut")
class ShortcutController(private val service: ShortcutService) {

    @GetMapping("/categories")
    fun categories(@RequestParam(required = false) merchant: String?, principal: Principal): ShortcutCategoriesDTO =
        service.categories(principal, merchant)

    @PostMapping("/log")
    fun log(@RequestBody dto: ShortcutLogDTO, principal: Principal): ShortcutLogResultDTO = service.log(principal, dto)
}
