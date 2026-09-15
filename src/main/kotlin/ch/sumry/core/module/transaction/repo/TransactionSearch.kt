package ch.sumry.core.module.transaction.repo

import ch.sumry.core.module.transaction.dto.TransactionSearchQuery
import ch.sumry.core.module.transaction.dto.TransactionSums
import ch.sumry.core.module.transaction.model.Transaction
import ch.sumry.core.types.Money
import ch.sumry.core.types.TransactionKind
import ch.sumry.core.types.money
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.ZoneId
import java.util.*

/**
 * The filter is built once as a Specification and reused for the page query, the count,
 * and the sums, so the three can never disagree about what "matched".
 */
@Repository
class TransactionSearch(private val em: EntityManager) {

    fun specification(userId: UUID, q: TransactionSearchQuery): Specification<Transaction> =
        Specification { root, _, cb -> cb.and(*predicates(userId, q, root, cb).toTypedArray()) }

    fun pageable(q: TransactionSearchQuery): PageRequest {
        val direction = if (q.dir.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val property = if (q.sort.equals("amount", ignoreCase = true)) "amount" else "date"
        // A stable secondary key keeps paging deterministic when many rows share a date.
        return PageRequest.of(q.page.coerceAtLeast(0), q.size.coerceIn(1, 500), Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id")))
    }

    fun sums(userId: UUID, q: TransactionSearchQuery): TransactionSums {
        val cb = em.criteriaBuilder
        val query = cb.createTupleQuery()
        val root = query.from(Transaction::class.java)
        val where = predicates(userId, q, root, cb)

        val incomeCase = cb.selectCase<BigDecimal>()
            .`when`(cb.equal(root.get<TransactionKind>("kind"), TransactionKind.INCOME), root.get<BigDecimal>("amount"))
            .otherwise(BigDecimal.ZERO)
        val expenseCase = cb.selectCase<BigDecimal>()
            .`when`(cb.equal(root.get<TransactionKind>("kind"), TransactionKind.EXPENSE), root.get<BigDecimal>("amount"))
            .otherwise(BigDecimal.ZERO)

        query.multiselect(cb.sum(incomeCase), cb.sum(expenseCase), cb.count(root))
            .where(*where.toTypedArray())

        val row = em.createQuery(query).singleResult
        val income = (row.get(0) as BigDecimal?).money()
        val expense = (row.get(1) as BigDecimal?).money()
        return TransactionSums(
            income = income,
            expense = expense,
            net = (income - expense).money(),
            count = row.get(2) as Long,
        )
    }

    private fun predicates(
        userId: UUID,
        q: TransactionSearchQuery,
        root: Root<Transaction>,
        cb: CriteriaBuilder,
    ): List<Predicate> {
        val out = mutableListOf<Predicate>()
        val account = root.join<Any, Any>("userAccount")
        out += cb.equal(account.get<Any>("user").get<UUID>("id"), userId)

        q.accountId?.let { out += cb.equal(account.get<UUID>("id"), it) }
        q.categoryLabel?.takeIf { it.isNotBlank() }?.let {
            out += cb.equal(root.join<Any, Any>("category").get<String>("label"), it)
        }
        q.kind?.takeIf { it.isNotBlank() }?.let { kind ->
            val k = root.get<TransactionKind>("kind")
            out += when (kind.uppercase()) {
                "TRANSFER" -> k.`in`(TransactionKind.TRANSFER_IN, TransactionKind.TRANSFER_OUT)
                "INCOME" -> cb.equal(k, TransactionKind.INCOME)
                "EXPENSE" -> cb.equal(k, TransactionKind.EXPENSE)
                else -> cb.conjunction()
            }
        }
        val zone = ZoneId.systemDefault()
        q.from?.let { out += cb.greaterThanOrEqualTo(root.get("date"), Date.from(it.atStartOfDay(zone).toInstant())) }
        q.to?.let { out += cb.lessThan(root.get("date"), Date.from(it.plusDays(1).atStartOfDay(zone).toInstant())) }
        q.min?.let { out += cb.greaterThanOrEqualTo(root.get("amount"), it) }
        q.max?.let { out += cb.lessThanOrEqualTo(root.get("amount"), it) }

        q.q?.trim()?.takeIf { it.isNotEmpty() }?.let { needle ->
            val pattern = "%${needle.lowercase()}%"
            val category = root.join<Any, Any>("category", jakarta.persistence.criteria.JoinType.LEFT)
            out += cb.or(
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(category.get("label"), "")), pattern),
            )
        }
        return out
    }

    companion object {
        val ZERO: BigDecimal = Money.ZERO
    }
}
