package ch.sumry.core.module.user.service

import ch.sumry.core.module.user.model.Plan
import ch.sumry.core.module.user.model.Role
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.repo.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * Creates the first admin on a fresh instance, so a self-hoster can sign in while
 * registration is closed.
 *
 * Runs only when `SUMRY_BOOTSTRAP_ADMIN_EMAIL` is set and the users table is empty. The
 * password is generated, printed to the log once and never stored in plain text; change it
 * after the first login. On every later start the table is no longer empty and this does
 * nothing, so leaving the variable set is harmless.
 */
@Component
class BootstrapAdmin(
    private val repo: UserRepository,
    @Value("\${sumry.bootstrap-admin.email:}")
    private val email: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(BootstrapAdmin::class.java)

    override fun run(args: ApplicationArguments) {
        create()
    }

    /** Returns the generated password when an admin was created, null otherwise. */
    fun create(): String? {
        val address = email.trim()
        if (address.isEmpty()) return null
        if (repo.count() > 0) {
            log.info("Bootstrap admin skipped: users already exist")
            return null
        }

        val password = generatePassword()
        val user = User(
            username = address.substringBefore('@').ifBlank { "admin" },
            email = address,
            password = password,
            role = Role.ADMIN,
            plan = Plan.PREMIUM,
        )
        user.encodePassword(password)
        repo.save(user)

        log.warn(
            "Bootstrap admin created: email={} password={} - sign in and change this password now. " +
                "It is shown only once.",
            address, password,
        )
        return password
    }

    private fun generatePassword(): String {
        val random = SecureRandom()
        return (1..PASSWORD_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }

    private companion object {
        const val PASSWORD_LENGTH = 24
        // No look-alike characters, so the password can be typed off a terminal.
        const val ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
