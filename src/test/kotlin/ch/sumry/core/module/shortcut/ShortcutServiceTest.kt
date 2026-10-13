package ch.sumry.core.module.shortcut

import ch.sumry.core.module.shortcut.ShortcutService.Companion.matchAccount
import ch.sumry.core.module.shortcut.ShortcutService.Companion.merchantKey
import ch.sumry.core.module.shortcut.ShortcutService.Companion.parseAmount
import ch.sumry.core.module.user.model.UserAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ShortcutServiceTest {

    @Test
    fun `reads the amount whatever the locale wrote`() {
        assertEquals(BigDecimal("12.50"), parseAmount("CHF 12.50"))
        assertEquals(BigDecimal("12.50"), parseAmount("12,50 CHF"))
        assertEquals(BigDecimal("1234.50"), parseAmount("CHF 1'234.50"))
        assertEquals(BigDecimal("1234.50"), parseAmount("1.234,50"))
        assertEquals(BigDecimal("1234.50"), parseAmount("1,234.50"))
        assertEquals(BigDecimal("12.50"), parseAmount("-12.50"))
        assertNull(parseAmount("CHF"))
        assertNull(parseAmount("0.00"))
    }

    @Test
    fun `merchant key is the first meaningful word`() {
        assertEquals("migros", merchantKey("MIGROS M ZUERICH"))
        assertEquals("coop", merchantKey("Coop-1234 Bern"))
        assertEquals("sbb", merchantKey("SBB CFF FFS"))
        assertNull(merchantKey("12 34"))
    }

    @Test
    fun `card names find their account`() {
        val pf = UserAccount().apply { accountName = "PostFinance" }
        val rev = UserAccount().apply { accountName = "Revolut" }
        val cash = UserAccount().apply { accountName = "Portemonnaie" }
        val all = listOf(pf, rev, cash)
        assertEquals(pf, matchAccount(all, "PostFinance Visa Debit"))
        assertEquals(rev, matchAccount(all, "Revolut"))
        assertEquals(rev, matchAccount(all, "revolut metal"))
        assertNull(matchAccount(all, "UBS Card"))
        assertNull(matchAccount(all, null))
    }
}
