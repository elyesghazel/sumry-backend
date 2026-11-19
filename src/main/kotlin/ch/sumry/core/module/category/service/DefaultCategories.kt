package ch.sumry.core.module.category.service

import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.rule.model.CategoryRule
import ch.sumry.core.module.rule.model.MatchType
import ch.sumry.core.module.rule.repo.CategoryRuleRepository
import ch.sumry.core.module.user.model.User
import ch.sumry.core.types.CategoryType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The starter set a new account gets, so the first quick entry ("12.50 lunch cash") can be
 * booked without setting anything up first.
 *
 * Each category may come with a few everyday words as one low-priority rule. Rules the user
 * creates later get the default priority and run first. Only new accounts are seeded;
 * existing ones are never touched.
 */
@Service
class DefaultCategories(
    private val categories: CategoryRepository,
    private val rules: CategoryRuleRepository,
) {

    data class Default(
        val label: String,
        val type: CategoryType,
        val color: String,
        val keywords: List<String> = emptyList(),
    )

    @Transactional
    fun seed(user: User) {
        for (d in DEFAULTS) {
            val category = categories.save(
                Category(label = d.label, color = d.color, categoryType = d.type, user = user, isActive = true)
            )
            if (d.keywords.isEmpty()) continue
            rules.save(
                CategoryRule(
                    user = user,
                    pattern = d.keywords.joinToString("|", prefix = "\\b(", postfix = ")\\b"),
                    matchType = MatchType.REGEX,
                    category = category,
                    priority = RULE_PRIORITY,
                )
            )
        }
    }

    companion object {
        /** Behind the default of 100, so the user's own rules win. */
        const val RULE_PRIORITY = 1000

        val DEFAULTS = listOf(
            Default("Groceries", CategoryType.EXPENSE, "#4f8a3c", listOf("groceries", "supermarket", "migros", "coop", "aldi", "lidl", "denner")),
            Default("Eating out", CategoryType.EXPENSE, "#c0612b", listOf("lunch", "dinner", "breakfast", "restaurant", "cafe", "coffee", "kebab", "pizza", "takeaway")),
            Default("Transport", CategoryType.EXPENSE, "#3c6e9a", listOf("train", "bus", "tram", "taxi", "uber", "fuel", "petrol", "parking", "sbb")),
            Default("Housing", CategoryType.EXPENSE, "#7a5c3e", listOf("rent")),
            Default("Bills", CategoryType.EXPENSE, "#5f6b78", listOf("electricity", "internet", "insurance", "phone bill", "tax")),
            Default("Subscriptions", CategoryType.EXPENSE, "#7d4f9a", listOf("netflix", "spotify", "subscription")),
            Default("Health", CategoryType.EXPENSE, "#b2475f", listOf("pharmacy", "doctor", "dentist")),
            Default("Shopping", CategoryType.EXPENSE, "#9a6b1f", listOf("clothes", "shoes")),
            Default("Leisure", CategoryType.EXPENSE, "#2f8f86", listOf("cinema", "concert", "gym")),
            Default("Gifts", CategoryType.EXPENSE, "#c2410c", listOf("gift", "present")),
            Default("Other", CategoryType.EXPENSE, "#8a8a8a"),
            Default("Salary", CategoryType.INCOME, "#2e7d4f", listOf("salary", "wage", "paycheck")),
            Default("Other income", CategoryType.INCOME, "#6a9a3c"),
        )
    }
}
