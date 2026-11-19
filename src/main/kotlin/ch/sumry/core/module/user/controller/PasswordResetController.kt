package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.auth.PasswordResetDto
import ch.sumry.core.module.user.service.PasswordResetService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PasswordResetController(private val passwordResets: PasswordResetService) {

    /**
     * Sets a new password with a one-time token from an admin. Needs no bearer token:
     * `{"token": "...", "newPassword": "..."}`. Signs the user out everywhere.
     */
    @PostMapping("/api/users/password-reset")
    fun resetPassword(@Valid @RequestBody dto: PasswordResetDto): ResponseEntity<Unit> {
        passwordResets.consume(dto.token, dto.newPassword)
        return ResponseEntity.noContent().build()
    }
}
