package ch.sumry.core.privacy

/**
 * Account numbers never leave the backend in full and are never stored: statements,
 * notes and shortcut text all pass through here first.
 *
 * The patterns are mirrored in the V11 Flyway migration that cleaned existing rows, so a
 * change here has to be repeated there (and in the web/app `maskIban` helpers).
 */

/**
 * An IBAN: two letters, two check digits, then 11-30 alphanumerics, either packed or
 * printed in groups of four. Case-insensitive because people type them in lowercase.
 */
private val IBAN = Regex("(?<![A-Za-z0-9])[A-Za-z]{2}[0-9]{2}(?: ?[A-Za-z0-9]{4}){2,7}(?: ?[A-Za-z0-9]{1,3})?(?![A-Za-z0-9])")

/** Swiss postal account ("Postkonto"), e.g. 12-345678-9. */
private val POSTAL_ACCOUNT = Regex("(?<![0-9-])[0-9]{2}-[0-9]{1,6}-([0-9])(?![0-9-])")

/** A real IBAN carries a long run of digits; this keeps words like "ab12 kauf coop" readable. */
private const val MIN_IBAN_DIGITS = 6

/** "CH93 0076 2011 6238 5295 7" → "CH•• … 2957": country code and the last four characters. */
fun maskIban(text: String): String = IBAN.replace(text) { m ->
    val compact = m.value.replace(" ", "").uppercase()
    if (compact.count { it.isDigit() } < MIN_IBAN_DIGITS) m.value
    else compact.take(2) + "•• … " + compact.takeLast(4)
}

@JvmName("maskIbanOrNull")
fun maskIban(text: String?): String? = text?.let { maskIban(it) }

/** "12-345678-9" → "••-••••••-9". */
fun maskPostalAccount(text: String): String = POSTAL_ACCOUNT.replace(text) { m -> "••-••••••-" + m.groupValues[1] }

/** Both IBANs and postal account numbers; what every free-text field goes through. */
fun maskAccounts(text: String): String = maskPostalAccount(maskIban(text))

@JvmName("maskAccountsOrNull")
fun maskAccounts(text: String?): String? = text?.let { maskAccounts(it) }
