package ch.sumry.core.module.user.dto

data class PreferencesResponseDto(
    val preferredCurrency: String,
    val theme: String,
    val language: String,
    val privacy: String,
    val appLock: Boolean,
    val lockDelaySeconds: Int,
    val timeframe: String,
)

/** Every field is optional: clients send only what changed. */
data class PreferencesUpdateDto(
    val preferredCurrency: String? = null,
    val theme: String? = null,
    val language: String? = null,
    val privacy: String? = null,
    val appLock: Boolean? = null,
    val lockDelaySeconds: Int? = null,
    val timeframe: String? = null,
)
