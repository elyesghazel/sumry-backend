package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.UserCreateDto
import ch.sumry.core.module.user.dto.UserResponseDto
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.service.UserService
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/users")
class UserController(private val service: UserService) {

    @GetMapping
    fun getAll(): List<User> = service.getAll()

    @PostMapping
    fun create(@RequestBody dto: UserCreateDto): UserResponseDto =
        service.create(dto)
}