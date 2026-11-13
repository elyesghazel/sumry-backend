package ch.sumry.core.module.porting

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

/**
 * Builds statement PDFs for tests. No real statement is ever used: these mimic the
 * PostFinance layout (left-aligned date and text, right-aligned amount columns).
 */
class SyntheticPdf {
    private val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val pages = ArrayList<MutableList<Triple<Float, Float, String>>>()

    fun page(block: PageBuilder.() -> Unit): SyntheticPdf {
        val items = ArrayList<Triple<Float, Float, String>>()
        PageBuilder(items).block()
        pages += items
        return this
    }

    inner class PageBuilder(private val items: MutableList<Triple<Float, Float, String>>) {
        /** Left-aligned text at [x], [y] points from the top. */
        fun text(x: Float, y: Float, s: String) { items += Triple(x, y, s) }
        /** Right-aligned text ending at [right]. */
        fun right(right: Float, y: Float, s: String) { items += Triple(right - width(s), y, s) }
    }

    fun width(s: String): Float = font.getStringWidth(s) / 1000f * SIZE

    fun bytes(): ByteArray = PDDocument().use { doc ->
        for (items in pages) {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                for ((x, y, s) in items) {
                    cs.beginText()
                    cs.setFont(font, SIZE)
                    cs.newLineAtOffset(x, page.mediaBox.height - y)
                    cs.showText(s)
                    cs.endText()
                }
            }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    companion object {
        const val SIZE = 9f
    }
}

/** Column positions of the synthetic PostFinance layout. */
object PfLayout {
    const val DATE = 40f
    const val TEXT = 92f
    const val CREDIT = 380f
    const val DEBIT = 445f
    const val VALUTA = 455f
    const val BALANCE = 555f
    const val LINE = 11f

    fun SyntheticPdf.PageBuilder.header(y: Float) {
        text(DATE, y, "Datum")
        text(TEXT, y, "Text")
        right(CREDIT, y, "Gutschrift")
        right(DEBIT, y, "Lastschrift")
        text(VALUTA, y, "Valuta")
        right(BALANCE, y, "Saldo")
    }

    /** One booking: first line with figures, then the continuation texts. Returns the next free y. */
    fun SyntheticPdf.PageBuilder.booking(
        y: Float, date: String?, texts: List<String>, credit: String? = null, debit: String? = null, balance: String? = null,
    ): Float {
        date?.let { text(DATE, y, it) }
        text(TEXT, y, texts.first())
        credit?.let { right(CREDIT, y, it) }
        debit?.let { right(DEBIT, y, it) }
        date?.let { text(VALUTA, y, it) }
        balance?.let { right(BALANCE, y, it) }
        var next = y + LINE
        for (t in texts.drop(1)) { text(TEXT, next, t); next += LINE }
        return next + 3f
    }
}
