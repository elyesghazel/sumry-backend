package ch.sumry.core.module.fx

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fx")
class FxController(private val fx: FxService) {
    @GetMapping
    fun rates(@RequestParam(defaultValue = "CHF") base: String): FxRates = fx.rates(base)
}
