package ch.sumry.core.module.insights.controller

import ch.sumry.core.module.insights.dto.ForecastDTO
import ch.sumry.core.module.insights.dto.SubscriptionsDTO
import ch.sumry.core.module.insights.service.InsightsService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.time.LocalDate

@RestController
@RequestMapping("/api/insights")
class InsightsController(private val service: InsightsService) {

    @GetMapping("/subscriptions")
    fun subscriptions(principal: Principal): SubscriptionsDTO = service.subscriptions(principal)

    @GetMapping("/forecast")
    fun forecast(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        principal: Principal,
    ): ForecastDTO = service.forecast(principal, date)
}
