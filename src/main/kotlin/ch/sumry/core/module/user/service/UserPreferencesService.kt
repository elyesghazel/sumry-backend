package ch.sumry.core.module.user.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.PreferencesResponseDto
import ch.sumry.core.module.user.dto.PreferencesUpdateDto
import ch.sumry.core.module.user.model.UserPreferences
import ch.sumry.core.module.user.repo.UserPreferencesRepository
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.Principal
import java.util.*

@Service
class UserPreferencesService(
    private val repo: UserPreferencesRepository,
    private val userRepository: UserRepository,
) {

    /** Accepted values, so a typo cannot put the clients into an unknown state. */
    private val themes = setOf("system", "light", "dark")
    private val privacies = setOf("off", "balances", "all")
    private val timeframes = setOf("7d", "30d", "month", "3m", "6m", "year")

    /** Returns the caller's preferences, creating the defaults on first read. */
    @Transactional
    fun get(principal: Principal): PreferencesResponseDto {
        val userId = UUID.fromString(principal.name)
        return toDto(loadOrCreate(userId))
    }

    @Transactional
    fun update(dto: PreferencesUpdateDto, principal: Principal): PreferencesResponseDto {
        val userId = UUID.fromString(principal.name)
        val prefs = loadOrCreate(userId)

        dto.preferredCurrency?.let { prefs.preferredCurrency = it.trim().uppercase() }
        dto.language?.let { prefs.language = it.trim().lowercase() }
        dto.theme?.let { prefs.theme = requireOneOf(it, themes, "theme") }
        dto.privacy?.let { prefs.privacy = requireOneOf(it, privacies, "privacy") }
        dto.timeframe?.let { prefs.timeframe = requireOneOf(it, timeframes, "timeframe") }
        dto.appLock?.let { prefs.appLock = it }
        dto.lockDelaySeconds?.let {
            if (it < 0 || it > 3600) {
                throw RequestException(HttpStatus.BAD_REQUEST, "lockDelaySeconds must be between 0 and 3600")
            }
            prefs.lockDelaySeconds = it
        }

        return toDto(repo.save(prefs))
    }

    private fun loadOrCreate(userId: UUID): UserPreferences =
        repo.findByUser_Id(userId).orElseGet {
            val user = userRepository.findById(userId).orElseThrow {
                RequestException(HttpStatus.NOT_FOUND, "User not found")
            }
            repo.save(UserPreferences(user = user))
        }

    private fun requireOneOf(value: String, allowed: Set<String>, field: String): String {
        val normalised = value.trim().lowercase()
        if (normalised !in allowed) {
            throw RequestException(HttpStatus.BAD_REQUEST, "$field must be one of ${allowed.joinToString()}")
        }
        return normalised
    }

    private fun toDto(p: UserPreferences) = PreferencesResponseDto(
        preferredCurrency = p.preferredCurrency,
        theme = p.theme,
        language = p.language,
        privacy = p.privacy,
        appLock = p.appLock,
        lockDelaySeconds = p.lockDelaySeconds,
        timeframe = p.timeframe,
    )
}
