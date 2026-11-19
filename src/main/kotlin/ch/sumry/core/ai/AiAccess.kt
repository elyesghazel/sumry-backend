package ch.sumry.core.ai

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.util.*

/**
 * Who may use the AI features. The Claude check runs through a private bridge on the host, so it
 * only serves the people listed in SUMRY_AI_USERS (comma-separated user ids or emails, case
 * insensitive). Empty, the default, means AI is off for everyone.
 */
@Component
class AiAccess(
    @Value("\${sumry.ai.users:}") users: String,
    private val userRepository: UserRepository,
) {
    private val entries: Set<String> = users.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    private val emails: Set<String> = entries.filter { '@' in it }.toSet()

    fun isAllowed(userId: UUID): Boolean {
        if (entries.isEmpty()) return false
        if (userId.toString() in entries) return true
        if (emails.isEmpty()) return false
        val email = userRepository.findById(userId).orElse(null)?.email ?: return false
        return email.trim().lowercase() in emails
    }

    fun requireAllowed(userId: UUID) {
        if (!isAllowed(userId)) throw RequestException(HttpStatus.FORBIDDEN, "AI features are not enabled for this account")
    }
}
