package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.auth.*
import ch.sumry.core.module.user.service.UserService
import ch.sumry.core.security.JwtAuthenticationFilter
import ch.sumry.core.security.TokenClaims
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*

@RestController
@RequestMapping("/api/users")
class UserController(private val service: UserService) {


    @GetMapping("/validate")
    fun validateToken(principal: Principal?): ResponseEntity<UserResponseDto> {

        // when coming to this func token is valid bcs jwtfilter

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        // load user
        val userId = UUID.fromString(principal.name)
        val userDto = service.getById(userId, principal)

        // return 200 if ok
        return ResponseEntity.ok(userDto)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    fun getAll(): List<UserResponseDto> = service.getAll()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String, principal: Principal): UserResponseDto =
        service.getById(UUID.fromString(id), principal)

    @PostMapping("/register")
    fun register(@Valid @RequestBody dto: UserCreateDto, request: HttpServletRequest): UserResponseDto =
        service.register(dto, request)

    @PutMapping("/{userId}")
    fun update(@Valid @RequestBody dto: UserUpdateDto, @PathVariable userId: String, principal: Principal): UserResponseDto =
        service.update(userId, dto, principal)

    @PutMapping("/password")
    fun changePassword(
        @Valid @RequestBody dto: PasswordChangeDto,
        principal: Principal
    ): ResponseEntity<LoginResponseDto> = ResponseEntity.ok(service.changePassword(dto, principal))

    /**
     * Deletes the caller's account and every row of their data. Needs the current password
     * in the body: `{"password": "..."}`.
     */
    @DeleteMapping("/me")
    fun deleteMe(@Valid @RequestBody dto: AccountDeleteDto, principal: Principal): ResponseEntity<Unit> {
        service.deleteOwnAccount(dto, principal)
        return ResponseEntity.noContent().build()
    }

    /** Signs out this device: the token used for the call stops working immediately. */
    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Unit> {
        service.logout(request.getAttribute(JwtAuthenticationFilter.CLAIMS_ATTRIBUTE) as? TokenClaims)
        return ResponseEntity.noContent().build()
    }

    /** Signs out every device at once. */
    @PostMapping("/logout-all")
    fun logoutEverywhere(principal: Principal): ResponseEntity<Unit> {
        service.logoutEverywhere(principal)
        return ResponseEntity.noContent().build()
    }

    /** A token for Apple Shortcuts that can only reach /api/shortcut. */
    @PostMapping("/shortcut-token")
    fun shortcutToken(principal: Principal): ShortcutTokenDto = service.issueShortcutToken(principal)

    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: LoginDto, request: HttpServletRequest): LoginResponseDto =
        service.login(dto, request)
}
