package ch.sumry.core.module.user.controller

import ch.sumry.core.module.user.dto.auth.MeDto
import ch.sumry.core.module.user.service.MeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/users/me")
class MeController(private val service: MeService) {

    @GetMapping
    fun me(principal: Principal): MeDto = service.me(principal)
}
