package ch.sumry.core.module.statement

import java.math.BigDecimal
import java.time.LocalDate

data class StatementLine(val label: String, val amount: BigDecimal, val share: Double, val color: String?)
data class StatementEntry(val date: LocalDate, val description: String?, val category: String?, val account: String?, val amount: BigDecimal)
data class StatementBudget(val category: String, val limit: BigDecimal, val spent: BigDecimal, val over: Boolean)
data class StatementAccount(val name: String, val currency: String, val balance: BigDecimal, val baseBalance: BigDecimal, val change: BigDecimal)

data class StatementDTO(
    val month: String,
    val from: LocalDate,
    val to: LocalDate,
    val baseCurrency: String,
    val income: BigDecimal,
    val expense: BigDecimal,
    val net: BigDecimal,
    /** Net as a share of income; null without income. */
    val savingsRate: Double?,
    val previousIncome: BigDecimal,
    val previousExpense: BigDecimal,
    val byCategory: List<StatementLine>,
    val largest: List<StatementEntry>,
    val budgets: List<StatementBudget>,
    val subscriptionsMonthly: BigDecimal,
    val accounts: List<StatementAccount>,
    val netWorth: BigDecimal,
    val netWorthChange: BigDecimal,
    val entryCount: Int,
)
