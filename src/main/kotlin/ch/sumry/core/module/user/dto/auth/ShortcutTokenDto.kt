package ch.sumry.core.module.user.dto.auth

import java.util.*

data class ShortcutTokenDto(val token: String, val expiresAt: Date)
