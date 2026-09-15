package ch.sumry.core.module.goal.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class GoalCreateDTO(
    @field:NotBlank val name: String,
    @field:Positive val targetAmount: BigDecimal,
    val targetDate: LocalDate? = null,
    val accountId: UUID? = null,
    /** Starting point for an unlinked goal (money already set aside). */
    val savedAmount: BigDecimal? = null,
    val color: String? = null,
    val icon: String? = null,
)

data class GoalUpdateDTO(
    val name: String? = null,
    val targetAmount: BigDecimal? = null,
    val targetDate: LocalDate? = null,
    val clearTargetDate: Boolean = false,
    val accountId: UUID? = null,
    val clearAccount: Boolean = false,
    val savedAmount: BigDecimal? = null,
    val color: String? = null,
    val icon: String? = null,
    val isActive: Boolean? = null,
)

/** Put money towards the goal. With a linked goal, [fromAccountId] makes it a real transfer. */
data class GoalContributeDTO(
    @field:Positive val amount: BigDecimal,
    val fromAccountId: UUID? = null,
    val note: String? = null,
)

data class GoalResponseDTO(
    val id: UUID,
    val name: String,
    val targetAmount: BigDecimal,
    val targetDate: LocalDate?,
    val accountId: UUID?,
    val accountName: String?,
    val current: BigDecimal,
    val remaining: BigDecimal,
    val percentage: Double,
    val monthsLeft: Int?,
    /** What has to go in each month from now to hit the target on the date; null without a date. */
    val requiredPerMonth: BigDecimal?,
    /** Whether the current amount is at least where a straight line to the target would be today. */
    val onTrack: Boolean?,
    val achieved: Boolean,
    val color: String?,
    val icon: String?,
    val isActive: Boolean,
    val createdAt: Date,
)
