package ch.sumry.core.module.porting

import ch.sumry.core.types.TransactionKind
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.abs

/**
 * Lines statement rows up with entries that were already logged by hand.
 *
 * Hand-logged entries rarely share the bank's wording ("Kebab" vs "APPLE PAY KAUF ... RPS GMBH")
 * and are often dated a day or two before the bank books them, so text and exact day are not
 * used. A row matches an entry with the same amount, a compatible direction (a transfer out is
 * money leaving like an expense) and a date within [WINDOW_DAYS] of the row's purchase or booking
 * day. Every entry is used at most once, closest date first. Rows still open afterwards may be
 * two or three entries added up (a Revolut top-up plus its fee), which a second pass looks for.
 */
object ImportMatcher {

    const val WINDOW_DAYS = 3L
    /** The split search tries combinations of at most this many nearby candidates. */
    private const val SPLIT_POOL = 14

    data class Entry(
        val id: UUID,
        val date: LocalDate,
        val amount: BigDecimal,
        val kind: TransactionKind,
        val label: String,
    )

    enum class Kind { SAME_DAY, NEAR, SPLIT }

    data class Match(val ids: List<UUID>, val kind: Kind, val note: String)

    private val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun compatible(row: TransactionKind, entry: TransactionKind): Boolean = when (row) {
        TransactionKind.EXPENSE, TransactionKind.TRANSFER_OUT -> entry == TransactionKind.EXPENSE || entry == TransactionKind.TRANSFER_OUT
        TransactionKind.INCOME, TransactionKind.TRANSFER_IN -> entry == TransactionKind.INCOME || entry == TransactionKind.TRANSFER_IN
    }

    private fun distance(row: ParsedRow, d: LocalDate): Long {
        val a = abs(ChronoUnit.DAYS.between(row.date, d))
        val b = row.bookingDate?.let { abs(ChronoUnit.DAYS.between(it, d)) } ?: a
        return minOf(a, b)
    }

    private fun describe(e: Entry) = "${e.label} · ${e.date.format(day)}"

    /** Matches by row index. Rows without a match are absent. */
    fun match(rows: List<ParsedRow>, entries: List<Entry>): Map<Int, Match> {
        val result = HashMap<Int, Match>()
        val used = HashSet<UUID>()

        // Pass 1: one row, one entry, same amount.
        data class Pair(val row: Int, val entry: Entry, val dist: Long, val sameKind: Boolean)
        val pairs = ArrayList<Pair>()
        for ((i, r) in rows.withIndex()) for (e in entries) {
            if (e.amount.compareTo(r.amount) != 0 || !compatible(r.kind, e.kind)) continue
            val d = distance(r, e.date)
            if (d <= WINDOW_DAYS) pairs += Pair(i, e, d, e.kind == r.kind)
        }
        pairs.sortWith(compareBy<Pair> { it.dist }.thenBy { if (it.sameKind) 0 else 1 }.thenBy { it.row })
        for (p in pairs) {
            if (p.row in result || p.entry.id in used) continue
            used += p.entry.id
            result[p.row] = Match(listOf(p.entry.id), if (p.dist == 0L) Kind.SAME_DAY else Kind.NEAR, describe(p.entry))
        }

        // Pass 2: one row that is two or three entries added up, largest rows first.
        val open = rows.indices.filter { it !in result }.sortedByDescending { rows[it].amount }
        for (i in open) {
            val r = rows[i]
            val pool = entries.asSequence()
                .filter { it.id !in used && compatible(r.kind, it.kind) && it.amount.signum() > 0 && it.amount < r.amount }
                .map { it to distance(r, it.date) }
                .filter { it.second <= WINDOW_DAYS }
                .sortedBy { it.second }
                .take(SPLIT_POOL)
                .toList()
            var best: List<kotlin.Pair<Entry, Long>>? = null
            fun consider(combo: List<kotlin.Pair<Entry, Long>>) {
                if (combo.fold(BigDecimal.ZERO) { s, c -> s + c.first.amount }.compareTo(r.amount) != 0) return
                val cur = best
                if (cur == null || combo.size < cur.size || (combo.size == cur.size && combo.sumOf { it.second } < cur.sumOf { it.second })) best = combo
            }
            for (a in pool.indices) for (b in a + 1 until pool.size) {
                consider(listOf(pool[a], pool[b]))
                for (c in b + 1 until pool.size) consider(listOf(pool[a], pool[b], pool[c]))
            }
            val combo = best ?: continue
            combo.forEach { used += it.first.id }
            result[i] = Match(
                combo.map { it.first.id },
                Kind.SPLIT,
                combo.joinToString(" + ") { "${it.first.label} ${it.first.amount.toPlainString()}" } +
                    " · " + combo.first().first.date.format(day),
            )
        }
        return result
    }

    /** Opening balance plus every row, signed; null without an opening balance. */
    fun computedClosing(opening: BigDecimal?, rows: List<ParsedRow>): BigDecimal? = opening?.let { o ->
        rows.fold(o) { s, r ->
            if (r.kind == TransactionKind.INCOME || r.kind == TransactionKind.TRANSFER_IN) s + r.amount else s - r.amount
        }
    }
}
