package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.*
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.service.UserService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(private val service: UserService) {

    @GetMapping
    fun getAll(): List<User> = service.getAll()

    @PostMapping
    fun create(@Valid @RequestBody dto: UserCreateDto): UserResponseDto =
        service.create(dto)

    @PutMapping()
    fun update(@Valid @RequestBody dto: UserUpdateDto): UserResponseDto =
        service.update(dto)

    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: LoginDto): LoginResponseDto =
        service.login(dto)
}
