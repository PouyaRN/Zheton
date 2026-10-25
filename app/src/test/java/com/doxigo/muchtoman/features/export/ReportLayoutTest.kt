package com.doxigo.muchtoman.features.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pagination and right-to-left column order, checked without a Canvas.
 *
 * These are the two things that go wrong silently in a generated PDF: a table whose figures sit
 * to the left of their labels, and a row that runs off the bottom margin because the page
 * arithmetic used points per line while the renderer used pixels. Both are pure numbers here,
 * so they can be asserted to a tolerance instead of being eyeballed on a phone.
 */
class ReportLayoutTest {

    private val geometry = PageGeometry()
    private val style = LayoutStyle()
    private val tolerance = 0.05f

    private fun document(rowCount: Int, columns: Int = 3): ReportDocument {
        val columnList = (1..columns).map { index ->
            ReportColumn(
                headerFa = "ستون $index",
                kind = if (index == 1) CellKind.TEXT else CellKind.MONEY,
                widthChars = 20,
            )
        }
        val rows = (1..rowCount).map { row ->
            ReportRow(
                columnList.mapIndexed { index, _ ->
                    if (index == 0) textCell("ردیف $row") else moneyCell(row * 10_000L)
                },
            )
        }
        return ReportDocument(
            titleFa = "گزارش مالی",
            periodFa = "مرداد ۱۴۰۵ · ۳۱ روز",
            metaFa = "ساخته‌شده در ۱۴۰۵/۰۵/۰۳",
            footnoteFa = "فقط از تراکنش‌های ثبت‌شده ساخته شده است.",
            range = monthRange(1405, 5),
            sections = listOf(
                ReportSection("خلاصهٔ بازه", columnList, rows),
            ),
            generatedAt = 0L,
        )
    }

    @Test
    fun `the first column is the rightmost and every table ends on the left margin`() {
        val page = ReportLayout.paginate(document(rowCount = 3), geometry, style).first()
        val header = page.rows.first { it.cells.firstOrNull()?.role == TextRole.TABLE_HEADER }

        assertEquals(3, header.cells.size)
        // First column touches the right margin …
        assertEquals(geometry.contentRight, header.cells[0].right, tolerance)
        // … the next begins exactly where the first ended, moving leftwards …
        assertEquals(header.cells[0].left, header.cells[1].right, tolerance)
        assertEquals(header.cells[1].left, header.cells[2].right, tolerance)
        // … and the last column's left edge is the left margin, not a rounding shortfall.
        assertEquals(geometry.contentLeft, header.cells[2].left, tolerance)
    }

    @Test
    fun `rows never cross the bottom margin`() {
        val pages = ReportLayout.paginate(document(rowCount = 90), geometry, style)
        assertTrue("90 rows must not fit on one A4 page", pages.size > 1)
        for (page in pages) {
            for (row in page.rows) {
                assertTrue(
                    "row at ${row.top} with height ${row.height} crosses ${page.bodyBottom}",
                    row.top + row.height <= page.bodyBottom + tolerance,
                )
            }
        }
    }

    @Test
    fun `a table broken across pages repeats its column headers`() {
        val pages = ReportLayout.paginate(document(rowCount = 90), geometry, style)
        // The first page opens with the section title, then the headers.
        assertEquals(TextRole.SECTION, pages[0].rows.first().cells.first().role)
        assertEquals(TextRole.TABLE_HEADER, pages[0].rows[1].cells.first().role)
        // A continuation page starts with the header row, because the section title was left on
        // the previous page on purpose.
        assertEquals(TextRole.TABLE_HEADER, pages[1].rows.first().cells.first().role)
    }

    @Test
    fun `section titles and the document title span the full content width`() {
        val page = ReportLayout.paginate(document(rowCount = 2), geometry, style).first()

        val sectionTitle = page.rows.first { it.cells.first().role == TextRole.SECTION }
        assertEquals(1, sectionTitle.cells.size)
        assertEquals(geometry.contentLeft, sectionTitle.cells[0].left, tolerance)
        assertEquals(geometry.contentWidth, sectionTitle.cells[0].width, tolerance)

        val documentTitle = page.header.first { it.role == TextRole.TITLE }
        assertEquals(geometry.contentWidth, documentTitle.width, tolerance)
        assertEquals(geometry.contentRight, documentTitle.right, tolerance)
    }

    @Test
    fun `page numbers are stamped on every page after pagination`() {
        val pages = ReportLayout.paginate(document(rowCount = 90), geometry, style)
        pages.forEachIndexed { index, page ->
            assertEquals(index + 1, page.pageNumber)
            assertEquals(pages.size, page.pageCount)
            val label = page.footer.first().text
            assertTrue("footer should name this page: $label", label.contains("صفحه"))
            assertTrue("footer should name the total: $label", label.contains("از"))
        }
    }

    @Test
    fun `an empty report still produces one valid page`() {
        val empty = document(rowCount = 1).copy(sections = emptyList())
        val pages = ReportLayout.paginate(empty, geometry, style)
        assertEquals(1, pages.size)
        assertEquals(1, pages.single().pageNumber)
        assertEquals(1, pages.single().pageCount)
        // Title and period only: the generation line is on page 1 as well.
        assertEquals(TextRole.TITLE, pages.single().header.first().role)
    }
}
