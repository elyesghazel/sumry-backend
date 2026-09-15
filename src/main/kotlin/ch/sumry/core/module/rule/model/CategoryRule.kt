package ch.sumry.core.module.rule.model

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.User
import ch.sumry.core.module.user.model.UserAccount
import jakarta.persistence.*
import java.util.*

enum class MatchType {
    CONTAINS, STARTS_WITH, EQUALS, REGEX;

    fun matches(pattern: String, text: String): Boolean = when (this) {
        CONTAINS -> text.contains(pattern, ignoreCase = true)
        STARTS_WITH -> text.startsWith(pattern, ignoreCase = true)
        EQUALS -> text.equals(pattern, ignoreCase = true)
        REGEX -> runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text) }.getOrDefault(false)
    }
}

@Entity
@Table(name = "category_rules")
class CategoryRule(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Column(nullable = false)
    var pattern: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 16)
    var matchType: MatchType = MatchType.CONTAINS,

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    var category: Category? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    var account: UserAccount? = null,

    /** Lower runs first. */
    @Column(nullable = false)
    var priority: Int = 100,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    /** How often this rule has decided a category — the cheapest signal of whether it earns its place. */
    @Column(nullable = false)
    var hits: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Date = Date(),
) {
    fun matches(description: String?, accountId: UUID?): Boolean {
        if (!isActive || description.isNullOrBlank()) return false
        if (account != null && account?.id != accountId) return false
        return matchType.matches(pattern, description.trim())
    }
}
