package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.PreferencesResponseDto
import ch.sumry.core.module.user.dto.PreferencesUpdateDto
import ch.sumry.core.module.user.service.UserPreferencesService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/users/preferences")
class UserPreferencesController(private val service: UserPreferencesService) {

    @GetMapping
    fun get(principal: Principal): PreferencesResponseDto = service.get(principal)

    @PutMapping
    fun update(
        @Valid @RequestBody dto: PreferencesUpdateDto,
        principal: Principal
    ): PreferencesResponseDto = service.update(dto, principal)
}
