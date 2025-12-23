package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.auth.*
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.service.UserService
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
    fun getAll(): List<User> = service.getAll()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String, principal: Principal): UserResponseDto =
        service.getById(UUID.fromString(id), principal)

    @PostMapping("/register")
    fun register(@Valid @RequestBody dto: UserCreateDto): UserResponseDto =
        service.register(dto)

    @PutMapping("/{userId}")
    fun update(@Valid @RequestBody dto: UserUpdateDto, @PathVariable userId: String, principal: Principal): UserResponseDto =
        service.update(userId, dto, principal)

    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: LoginDto): LoginResponseDto =
        service.login(dto)
}
