package ch.sumry.core.module.shortcut

import java.util.*

/** Expense categories for a "Choose from List", the suggestion for the merchant first. */
data class ShortcutCategoriesDTO(val suggested: String?, val categories: List<String>)

/**
 * What the Wallet "Transaction" automation hands over. [amount] is taken as text because
 * Shortcuts passes "CHF 12.50" or "12,50" depending on the locale.
 */
data class ShortcutLogDTO(
    val amount: String? = null,
    val category: String? = null,
    val merchant: String? = null,
    /** The Wallet card name; matched against account names when [account] is absent. */
    val card: String? = null,
    val account: String? = null,
)

data class ShortcutLogResultDTO(val transactionId: UUID?, val message: String)
