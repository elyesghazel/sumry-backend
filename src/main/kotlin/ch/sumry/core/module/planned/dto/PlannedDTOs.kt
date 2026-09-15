package ch.sumry.core.module.planned.dto

import ch.sumry.core.module.transaction.model.Frequency
import ch.sumry.core.types.TransactionKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class PlannedCreateDTO(
    @field:NotBlank val name: String,
    @field:Positive val amount: BigDecimal,
    val description: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val frequency: Frequency,
    /** Required unless this is a transfer. */
    val categoryLabel: String? = null,
    val accountId: UUID,
    /** Set to plan a transfer into this account instead of an income/expense. */
    val toAccountId: UUID? = null,
    val autoBook: Boolean = false,
)

/** Every field optional: send only what changed. */
data class PlannedUpdateDTO(
    val name: String? = null,
    val amount: BigDecimal? = null,
    val description: String? = null,
    val startDate: LocalDate? = null,
    /** Explicit null clears the end date; use [clearEndDate] because JSON null and absent look alike here. */
    val endDate: LocalDate? = null,
    val clearEndDate: Boolean = false,
    val frequency: Frequency? = null,
    val categoryLabel: String? = null,
    val accountId: UUID? = null,
    val toAccountId: UUID? = null,
    val autoBook: Boolean? = null,
    val isActive: Boolean? = null,
)

data class PlannedResponseDTO(
    val id: UUID,
    val name: String,
    val amount: BigDecimal,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val frequency: Frequency,
    val kind: TransactionKind,
    val categoryLabel: String?,
    val categoryColor: String?,
    val accountId: UUID,
    val accountName: String,
    val toAccountId: UUID?,
    val toAccountName: String?,
    val autoBook: Boolean,
    val isActive: Boolean,
    val lastBookedOn: LocalDate?,
    val nextDate: LocalDate?,
    /** What this costs or brings per month, whatever its frequency. */
    val monthlyEquivalent: BigDecimal,
)

/** One dated instance of a plan. */
data class OccurrenceDTO(
    val plannedId: UUID,
    val name: String,
    val amount: BigDecimal,
    val kind: TransactionKind,
    val date: LocalDate,
    val categoryLabel: String?,
    val categoryColor: String?,
    val accountId: UUID,
    val accountName: String,
    val currency: String,
    val toAccountName: String?,
    val autoBook: Boolean,
    /** True when the date has passed and nothing was booked for it. */
    val overdue: Boolean,
)

data class SafeToSpendDTO(
    /** Balance the calculation started from, in the base currency: every account, or the one asked for. */
    val balance: BigDecimal,
    val accountId: UUID?,
    val today: LocalDate,
    /** The date the next planned income lands, or today + 30 days when none is planned. */
    val horizon: LocalDate,
    val daysToHorizon: Int,
    val horizonReason: String,
    val upcomingExpenses: BigDecimal,
    val upcomingTransfersOut: BigDecimal,
    val safeToSpend: BigDecimal,
    val perDay: BigDecimal,
    val upcoming: List<OccurrenceDTO>,
)
