package ch.sumry.core.types

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Amounts are exact decimals with two places, always. Every arithmetic result passes
 * through [money] so a division or a rate conversion can never leave a third decimal
 * behind to be summed later.
 */
object Money {
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2)

    fun of(value: BigDecimal?): BigDecimal = (value ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

    fun of(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)

    /** Anything under half a cent is rounding noise, not a difference. */
    fun isZero(value: BigDecimal?): Boolean = value == null || value.abs() < BigDecimal("0.005")
}

fun BigDecimal?.money(): BigDecimal = Money.of(this)

fun BigDecimal?.orZero(): BigDecimal = this ?: Money.ZERO

fun Iterable<BigDecimal?>.sumMoney(): BigDecimal =
    fold(Money.ZERO) { acc, v -> acc + (v ?: Money.ZERO) }.money()
