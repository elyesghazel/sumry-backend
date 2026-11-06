package ch.sumry.core.module.user.dto.accounts

import ch.sumry.core.types.Money
import java.math.BigDecimal

import jakarta.validation.constraints.NotBlank

data class UserAccountCreateDto(
    @field:NotBlank(message = "Account name cannot be blank")
    val accountName: String,
    val currentBalance: BigDecimal = Money.ZERO,
    val currency: String = "CHF",
    val spending: Boolean = true,
)
