package ch.sumry.core.module.rule.dto

import ch.sumry.core.module.rule.model.MatchType
import jakarta.validation.constraints.NotBlank
import java.util.*

data class RuleCreateDTO(
    @field:NotBlank val pattern: String,
    val matchType: MatchType = MatchType.CONTAINS,
    @field:NotBlank val categoryLabel: String,
    val accountId: UUID? = null,
    val priority: Int = 100,
)

data class RuleUpdateDTO(
    val pattern: String? = null,
    val matchType: MatchType? = null,
    val categoryLabel: String? = null,
    val accountId: UUID? = null,
    val clearAccount: Boolean = false,
    val priority: Int? = null,
    val isActive: Boolean? = null,
)

data class RuleResponseDTO(
    val id: UUID,
    val pattern: String,
    val matchType: MatchType,
    val categoryLabel: String,
    val categoryColor: String?,
    val categoryType: String,
    val accountId: UUID?,
    val accountName: String?,
    val priority: Int,
    val isActive: Boolean,
    val hits: Int,
)

data class RuleTestDTO(val description: String, val accountId: UUID? = null)

data class RuleMatchDTO(val rule: RuleResponseDTO?, val categoryLabel: String?)

data class RuleApplyChange(
    val transactionId: UUID,
    val date: Date?,
    val description: String?,
    val fromCategory: String?,
    val toCategory: String,
    val rulePattern: String,
)

data class RuleApplyResult(
    val dryRun: Boolean,
    val examined: Int,
    val changed: Int,
    /** A rule pointed at an income category for an expense (or vice versa); left alone. */
    val skippedKindMismatch: Int,
    val changes: List<RuleApplyChange>,
)
