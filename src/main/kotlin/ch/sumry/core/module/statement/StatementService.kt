package ch.sumry.core.module.statement

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.budget.service.BudgetService
import ch.sumry.core.module.fx.FxService
import ch.sumry.core.module.insights.service.InsightsService
import ch.sumry.core.module.plan.PlanService
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.module.transaction.service.TransactionService
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.types.Money
import ch.sumry.core.types.TransactionKind
import ch.sumry.core.types.money
import ch.sumry.core.types.orZero
import ch.sumry.core.types.sumMoney
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.Principal
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.*

@Service
class StatementService(
    private val transactions: TransactionRepository,
    private val transactionService: TransactionService,
    private val accounts: UserAccountRepository,
    private val users: UserRepository,
    private val budgets: BudgetService,
    private val insights: InsightsService,
    private val fx: FxService,
    private val mail: JavaMailSender,
    private val plans: PlanService,
    @Value("\${spring.mail.host:}") private val mailHost: String,
    @Value("\${sumry.statement.from:}") private val mailFrom: String,
    @Value("\${sumry.statement.scheduled:false}") private val scheduled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val zone: ZoneId = ZoneId.systemDefault()

    fun statement(principal: Principal, month: YearMonth): StatementDTO {
        val userId = UUID.fromString(principal.name)
        val base = transactionService.baseCurrency(userId)
        val all = transactions.findByUserAccount_User_Id(userId).filter { it.date != null }
        fun ym(d: Date) = YearMonth.from(d.toInstant().atZone(zone).toLocalDate())
        val rows = all.filter { ym(it.date!!) == month && !it.effectiveKind().isTransfer }
        val prev = all.filter { ym(it.date!!) == month.minusMonths(1) && !it.effectiveKind().isTransfer }

        val income = rows.filter { it.effectiveKind() == TransactionKind.INCOME }.map { it.baseAmount }.sumMoney()
        val expense = rows.filter { it.effectiveKind() == TransactionKind.EXPENSE }.map { it.baseAmount }.sumMoney()
        val net = (income - expense).money()

        val expenses = rows.filter { it.effectiveKind() == TransactionKind.EXPENSE }
        val byCat = expenses.groupBy { it.category?.label ?: "Uncategorised" }
            .map { (label, txs) -> Triple(label, txs.map { it.baseAmount }.sumMoney(), txs.firstOrNull()?.category?.color) }
            .sortedByDescending { it.second }
        val top = byCat.take(8).map { StatementLine(it.first, it.second, share(it.second, expense), it.third) }.toMutableList()
        val rest = byCat.drop(8)
        if (rest.isNotEmpty()) {
            val sum = rest.map { it.second }.sumMoney()
            top += StatementLine("Other (${rest.size})", sum, share(sum, expense), null)
        }
        val largest = expenses.sortedByDescending { it.baseAmount }.take(5).map {
            StatementEntry(it.date!!.toInstant().atZone(zone).toLocalDate(), it.description, it.category?.label, it.userAccount?.accountName, it.baseAmount)
        }

        // Balances at month end: today's balances with everything booked afterwards undone.
        val after = all.filter { ym(it.date!!) > month }
        val accountLines = accounts.findByUserId(userId).map { a ->
            val laterDelta = after.filter { it.userAccount?.id == a.id }.map { it.effectiveKind().signedEffect(it.amount.orZero()) }.sumMoney()
            val monthDelta = all.filter { it.userAccount?.id == a.id && ym(it.date!!) == month }.map { it.effectiveKind().signedEffect(it.amount.orZero()) }.sumMoney()
            val endBalance = (a.currentBalance.orZero() - laterDelta).money()
            StatementAccount(a.accountName ?: "", a.currency, endBalance, fx.convert(endBalance, a.currency, base), fx.convert(monthDelta, a.currency, base))
        }
        val netWorth = accountLines.map { it.baseBalance }.sumMoney()

        val budgetLines = budgets.getBudgetProgress(userId, month.atDay(15)).map {
            StatementBudget(it.categoryLabel, it.effectiveLimit, it.spentAmount, it.spentAmount > it.effectiveLimit)
        }

        return StatementDTO(
            month = month.toString(),
            from = month.atDay(1),
            to = month.atEndOfMonth(),
            baseCurrency = base,
            income = income,
            expense = expense,
            net = net,
            savingsRate = if (income.signum() > 0) net.divide(income, 4, RoundingMode.HALF_UP).toDouble() else null,
            previousIncome = prev.filter { it.effectiveKind() == TransactionKind.INCOME }.map { it.baseAmount }.sumMoney(),
            previousExpense = prev.filter { it.effectiveKind() == TransactionKind.EXPENSE }.map { it.baseAmount }.sumMoney(),
            byCategory = top,
            largest = largest,
            budgets = budgetLines,
            subscriptionsMonthly = insights.subscriptions(principal).monthlyTotal,
            accounts = accountLines,
            netWorth = netWorth,
            netWorthChange = accountLines.map { it.change }.sumMoney(),
            entryCount = rows.size,
        )
    }

    val mailConfigured: Boolean get() = mailHost.isNotBlank()

    fun send(principal: Principal, month: YearMonth, to: String?): String {
        if (!mailConfigured) throw RequestException(HttpStatus.SERVICE_UNAVAILABLE, "Mail is not configured on this instance (SUMRY_MAIL_HOST)")
        val userId = UUID.fromString(principal.name)
        plans.requireStatementEmail(userId)
        val user = users.findById(userId).orElseThrow()
        val recipient = to?.takeIf { it.isNotBlank() } ?: user.email
        val s = statement(principal, month)
        val message = mail.createMimeMessage()
        val helper = MimeMessageHelper(message, "UTF-8")
        helper.setTo(recipient)
        helper.setFrom(mailFrom.ifBlank { "sumry@localhost" })
        helper.setSubject("Sumry — ${monthName(month)}: ${sign(s.net)} ${fmt(s.net)} ${s.baseCurrency}")
        helper.setText(render(s), true)
        mail.send(message)
        return recipient
    }

    /** The 1st at 07:00: last month's statement to every user, when switched on and mail works. */
    @Scheduled(cron = "0 0 7 1 * *", zone = "Europe/Zurich")
    fun sendMonthly() {
        if (!scheduled || !mailConfigured) return
        val month = YearMonth.now().minusMonths(1)
        for (user in users.findAll()) {
            if (!plans.effective(user).limits.statementEmail) continue
            try {
                send(Principal { user.id.toString() }, month, null)
                log.info("Statement for {} sent to {}", month, user.email)
            } catch (ex: Exception) {
                log.error("Statement for {} failed: {}", user.email, ex.message)
            }
        }
    }

    // ------------------------------------------------------------- rendering

    fun render(s: StatementDTO): String {
        val c = s.baseCurrency
        fun row(label: String, value: String, muted: Boolean = false) =
            "<tr><td style=\"padding:6px 0;color:${if (muted) "#6b655c" else "#1a1814"}\">$label</td><td style=\"padding:6px 0;text-align:right;font-variant-numeric:tabular-nums\">$value</td></tr>"
        val cats = s.byCategory.joinToString("") { row(esc(it.label), "${fmt(it.amount)} <span style=\"color:#a39d93\">${(it.share * 100).toInt()}%</span>") }
        val big = s.largest.joinToString("") { row("${it.date.dayOfMonth}. ${esc(it.description ?: it.category ?: "")}", fmt(it.amount), muted = true) }
        val buds = if (s.budgets.isEmpty()) "" else "<h3 style=\"margin:26px 0 6px;font-weight:600\">Budgets</h3><table style=\"width:100%;border-collapse:collapse\">" +
            s.budgets.joinToString("") { row(esc(it.category), "${fmt(it.spent)} / ${fmt(it.limit)}" + if (it.over) " <span style=\"color:#a63d2f\">over</span>" else "") } + "</table>"
        val accs = s.accounts.joinToString("") { row(esc(it.name), "${fmt(it.balance)} ${it.currency}") }
        return """
<div style="font-family:Georgia,'Times New Roman',serif;max-width:560px;margin:0 auto;padding:28px 20px;color:#1a1814;background:#f4f1ea">
  <div style="font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#6b655c">Sumry · statement</div>
  <h1 style="font-weight:400;font-size:30px;margin:6px 0 18px">${monthName(YearMonth.parse(s.month))}</h1>
  <div style="font-size:44px;font-weight:300;letter-spacing:-.02em">${sign(s.net)}${fmt(s.net)} <span style="font-size:16px;color:#6b655c">$c</span></div>
  <div style="color:#6b655c;margin-bottom:22px">${fmt(s.income)} in · ${fmt(s.expense)} out${s.savingsRate?.let { " · ${(it * 100).toInt()}% saved" } ?: ""}</div>
  <hr style="border:0;border-top:1px solid #1a181433">
  <h3 style="margin:22px 0 6px;font-weight:600">Where it went</h3>
  <table style="width:100%;border-collapse:collapse">$cats</table>
  <h3 style="margin:26px 0 6px;font-weight:600">Largest</h3>
  <table style="width:100%;border-collapse:collapse">$big</table>
  $buds
  <h3 style="margin:26px 0 6px;font-weight:600">Accounts at month end</h3>
  <table style="width:100%;border-collapse:collapse">$accs${row("<strong>Net worth</strong>", "<strong>${fmt(s.netWorth)} $c</strong> <span style=\"color:#6b655c\">${sign(s.netWorthChange)}${fmt(s.netWorthChange)}</span>")}</table>
  <p style="color:#a39d93;font-size:12px;margin-top:30px">Subscriptions run at ${fmt(s.subscriptionsMonthly)} $c a month. ${s.entryCount} entries this month.</p>
</div>""".trimIndent()
    }

    private fun share(part: BigDecimal, whole: BigDecimal): Double =
        if (whole.signum() == 0) 0.0 else part.divide(whole, 4, RoundingMode.HALF_UP).toDouble()
    private fun fmt(v: BigDecimal): String = String.format(Locale.forLanguageTag("de-CH"), "%,.2f", v.abs()).replace(',', '\'')
    private fun sign(v: BigDecimal) = if (v.signum() < 0) "−" else if (v.signum() > 0) "+" else ""
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun monthName(m: YearMonth) = "${m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${m.year}"

    companion object {
        val ZERO: BigDecimal = Money.ZERO
    }
}
