package ch.sumry.core.module.transaction.model

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.privacy.maskAccounts
import ch.sumry.core.types.TransactionKind
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * Something that will happen to the money on a schedule: rent, salary, a subscription, the
 * monthly transfer to savings. It is not a transaction until an occurrence is booked.
 *
 * A row with [toAccount] set is a planned transfer from [userAccount] to [toAccount] and
 * carries no category; otherwise the category's type decides income or expense.
 */
@Entity
@Table(name = "planned_transactions")
class PlannedTransaction(
    @Id
    @GeneratedValue
    var id: UUID? = null,

    var name: String? = null,

    @Column(precision = 14, scale = 2)
    var amount: BigDecimal? = null,

    /** The note written onto each booked occurrence. Falls back to [name]. */
    var description: String? = null,

    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null, // null = indefinite

    @Enumerated(EnumType.STRING)
    var frequency: Frequency? = null,

    @ManyToOne
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id")
    var userAccount: UserAccount? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    var toAccount: UserAccount? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    /** Book occurrences automatically when they fall due; off means "remind me only". */
    @Column(name = "auto_book", nullable = false)
    var autoBook: Boolean = false,

    /** The latest occurrence date that has been booked, by hand or by the job. */
    @Column(name = "last_booked_on")
    var lastBookedOn: LocalDate? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Date = Date(),
) {
    val isTransfer: Boolean
        get() = toAccount != null

    /** The note is copied onto every booked occurrence, so it is masked like a transaction's. */
    @PrePersist
    @PreUpdate
    fun maskAccountNumbers() {
        description = maskAccounts(description)
    }

    fun kind(): TransactionKind = when {
        isTransfer -> TransactionKind.TRANSFER_OUT
        else -> category?.categoryType?.let { TransactionKind.of(it) } ?: TransactionKind.EXPENSE
    }

    /** Occurrence dates in `(after, until]`, oldest first, respecting the end date. */
    fun occurrencesBetween(after: LocalDate, until: LocalDate, limit: Int = 400): List<LocalDate> {
        val start = startDate ?: return emptyList()
        val freq = frequency ?: return emptyList()
        val stop = endDate?.let { minOf(it, until) } ?: until
        if (freq == Frequency.ONCE) {
            return if (start.isAfter(after) && !start.isAfter(stop)) listOf(start) else emptyList()
        }
        val out = ArrayList<LocalDate>()
        // Step from the anchor so month-end dates clamp the way people expect (31st → 30th).
        // Skipping straight to the neighbourhood of `after` keeps an old daily plan cheap.
        var i = maxOf(0L, freq.stepsBetween(start, after) - 1)
        while (out.size < limit) {
            val d = freq.advance(start, i++)
            if (d.isAfter(stop)) break
            if (d.isAfter(after)) out += d
        }
        return out
    }

    fun nextOccurrence(after: LocalDate): LocalDate? =
        occurrencesBetween(after, after.plusYears(5), limit = 1).firstOrNull()
}

enum class Frequency {
    DAILY, WEEKLY, MONTHLY, YEARLY, ONCE;

    fun advance(anchor: LocalDate, steps: Long): LocalDate = when (this) {
        DAILY -> anchor.plusDays(steps)
        WEEKLY -> anchor.plusWeeks(steps)
        MONTHLY -> anchor.plusMonths(steps)
        YEARLY -> anchor.plusYears(steps)
        ONCE -> anchor
    }

    /** A lower bound on the number of steps from [anchor] to [target]; never overshoots. */
    fun stepsBetween(anchor: LocalDate, target: LocalDate): Long = when (this) {
        DAILY -> java.time.temporal.ChronoUnit.DAYS.between(anchor, target)
        WEEKLY -> java.time.temporal.ChronoUnit.WEEKS.between(anchor, target)
        MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(anchor, target)
        YEARLY -> java.time.temporal.ChronoUnit.YEARS.between(anchor, target)
        ONCE -> 0
    }

    /** How many of these fit in a month, for "monthly equivalent" figures. */
    val perMonth: Double
        get() = when (this) {
            DAILY -> 30.44
            WEEKLY -> 4.348
            MONTHLY -> 1.0
            YEARLY -> 1.0 / 12
            ONCE -> 0.0
        }
}
