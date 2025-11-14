package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.auth.*
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.service.UserService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/users")
class UserController(private val service: UserService) {

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    fun getAll(): List<User> = service.getAll()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): UserResponseDto =
        service.getById(UUID.fromString(id))

    @PostMapping("/register")
    fun register(@Valid @RequestBody dto: UserCreateDto): UserResponseDto =
        service.register(dto)

    @PutMapping("/{userId}")
    fun update(@Valid @RequestBody dto: UserUpdateDto, @PathVariable userId: String): UserResponseDto =
        service.update(userId, dto)

    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: LoginDto): LoginResponseDto =
        service.login(dto)
}
