package ch.sumry.core.module.user.dto.accounts

import java.math.BigDecimal

data class UserAccountUpdateDto(
    val accountName: String? = null,
    val currentBalance: BigDecimal? = null,
)
