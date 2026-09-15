package ch.sumry.core.module.rule.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.rule.dto.*
import ch.sumry.core.module.rule.model.CategoryRule
import ch.sumry.core.module.rule.model.MatchType
import ch.sumry.core.module.rule.repo.CategoryRuleRepository
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.types.TransactionKind
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.Principal
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@Service
class RuleService(
    private val repo: CategoryRuleRepository,
    private val categories: CategoryRepository,
    private val accounts: UserAccountRepository,
    private val users: UserRepository,
    private val transactions: TransactionRepository,
) {
    fun list(principal: Principal): List<RuleResponseDTO> =
        repo.findByUser_IdOrderByPriorityAscCreatedAtAsc(UUID.fromString(principal.name)).map { toDto(it) }

    @Transactional
    fun create(dto: RuleCreateDTO, principal: Principal): RuleResponseDTO {
        val userId = UUID.fromString(principal.name)
        validatePattern(dto.pattern, dto.matchType)
        val rule = CategoryRule(
            user = users.getReferenceById(userId),
            pattern = dto.pattern.trim(),
            matchType = dto.matchType,
            category = category(dto.categoryLabel, userId),
            account = dto.accountId?.let { ownedAccount(it, userId) },
            priority = dto.priority,
        )
        return toDto(repo.save(rule))
    }

    @Transactional
    fun update(id: UUID, dto: RuleUpdateDTO, principal: Principal): RuleResponseDTO {
        val userId = UUID.fromString(principal.name)
        val rule = owned(id, userId)
        dto.matchType?.let { rule.matchType = it }
        dto.pattern?.let { validatePattern(it, rule.matchType); rule.pattern = it.trim() }
        dto.categoryLabel?.let { rule.category = category(it, userId) }
        if (dto.clearAccount) rule.account = null else dto.accountId?.let { rule.account = ownedAccount(it, userId) }
        dto.priority?.let { rule.priority = it }
        dto.isActive?.let { rule.isActive = it }
        return toDto(repo.save(rule))
    }

    @Transactional
    fun delete(id: UUID, principal: Principal) = repo.delete(owned(id, UUID.fromString(principal.name)))

    // ------------------------------------------------------------- matching

    /** The category the rules choose for a note, or null when none fires. Counts the hit. */
    @Transactional
    fun categorise(userId: UUID, description: String?, accountId: UUID?): Category? {
        val rule = firstMatch(userId, description, accountId) ?: return null
        rule.hits += 1
        repo.save(rule)
        return rule.category
    }

    fun test(dto: RuleTestDTO, principal: Principal): RuleMatchDTO {
        val rule = firstMatch(UUID.fromString(principal.name), dto.description, dto.accountId)
        return RuleMatchDTO(rule?.let { toDto(it) }, rule?.category?.label)
    }

    private fun firstMatch(userId: UUID, description: String?, accountId: UUID?): CategoryRule? =
        repo.findByUser_IdAndIsActiveTrueOrderByPriorityAscCreatedAtAsc(userId)
            .firstOrNull { it.matches(description, accountId) }

    /**
     * Re-runs the rules over history. Only the category changes, never the amount or the
     * account, so balances are untouched; a rule whose category has the wrong type for
     * the entry (income vs expense) is reported and skipped rather than flipping the sign.
     */
    @Transactional
    fun apply(principal: Principal, dryRun: Boolean, from: LocalDate?, to: LocalDate?): RuleApplyResult {
        val userId = UUID.fromString(principal.name)
        val rules = repo.findByUser_IdAndIsActiveTrueOrderByPriorityAscCreatedAtAsc(userId)
        if (rules.isEmpty()) return RuleApplyResult(dryRun, 0, 0, 0, emptyList())

        val zone = ZoneId.systemDefault()
        val fromDate = from?.let { Date.from(it.atStartOfDay(zone).toInstant()) }
        val toDate = to?.let { Date.from(it.plusDays(1).atStartOfDay(zone).toInstant()) }

        val changes = ArrayList<RuleApplyChange>()
        var examined = 0
        var mismatched = 0
        for (tx in transactions.findByUserAccount_User_Id(userId)) {
            val kind = tx.effectiveKind()
            if (kind.isTransfer) continue
            val d = tx.date
            if (fromDate != null && (d == null || d.before(fromDate))) continue
            if (toDate != null && (d == null || !d.before(toDate))) continue
            examined++
            val rule = rules.firstOrNull { it.matches(tx.description, tx.userAccount?.id) } ?: continue
            val target = rule.category ?: continue
            if (target.id == tx.category?.id) continue
            if (target.categoryType?.let { TransactionKind.of(it) } != kind) { mismatched++; continue }
            changes += RuleApplyChange(tx.id!!, tx.date, tx.description, tx.category?.label, target.label ?: "", rule.pattern)
            if (!dryRun) {
                tx.category = target
                transactions.save(tx)
                rule.hits += 1
            }
        }
        if (!dryRun) repo.saveAll(rules)
        return RuleApplyResult(dryRun, examined, changes.size, mismatched, changes)
    }

    // -------------------------------------------------------------- helpers

    private fun validatePattern(pattern: String, type: MatchType) {
        if (pattern.isBlank()) throw RequestException(HttpStatus.BAD_REQUEST, "pattern must not be blank")
        if (type == MatchType.REGEX) {
            runCatching { Regex(pattern) }.onFailure {
                throw RequestException(HttpStatus.BAD_REQUEST, "Invalid regular expression: ${it.message}")
            }
        }
    }

    private fun category(label: String, userId: UUID): Category =
        categories.findByLabelAndUser_IdAndIsActiveTrue(label, userId)
            .orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Category '$label' not found") }

    private fun ownedAccount(id: UUID, userId: UUID) =
        accounts.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Account not found") }
            .also { if (it.user?.id != userId) throw RequestException(HttpStatus.FORBIDDEN, "Access denied") }

    private fun owned(id: UUID, userId: UUID): CategoryRule {
        val rule = repo.findById(id).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Rule not found") }
        if (rule.user?.id != userId) throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        return rule
    }

    private fun toDto(r: CategoryRule) = RuleResponseDTO(
        id = r.id!!,
        pattern = r.pattern,
        matchType = r.matchType,
        categoryLabel = r.category?.label ?: "",
        categoryColor = r.category?.color,
        categoryType = r.category?.categoryType?.name ?: "EXPENSE",
        accountId = r.account?.id,
        accountName = r.account?.accountName,
        priority = r.priority,
        isActive = r.isActive,
        hits = r.hits,
    )
}
