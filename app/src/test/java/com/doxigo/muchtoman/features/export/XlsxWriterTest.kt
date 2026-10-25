package com.doxigo.muchtoman.features.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Proof that the bytes really are a workbook and not a zip-shaped guess.
 *
 * The file is unzipped in memory, every part is parsed by a real XML parser, and the assertions
 * are made against the parsed DOM rather than against a substring of the writer's own output —
 * which is the only way to catch a cell reference that is off by one or an attribute that is
 * spelled right in the StringBuilder and wrong in the file.
 */
class XlsxWriterTest {

    private val section = ReportSection(
        titleFa = "خلاصهٔ بازه",
        columns = listOf(
            ReportColumn("شرح", CellKind.TEXT, 26),
            ReportColumn("مقدار", CellKind.MONEY, 18),
        ),
        rows = listOf(
            ReportRow(listOf(textCell("درآمد بازه"), moneyCell(1_000_000))),
            ReportRow(listOf(textCell("خرج بازه"), moneyCell(400_000))),
            ReportRow(listOf(textCell("خالص"), moneyCell(600_000)), emphasis = true),
        ),
    )

    private val categorySection = ReportSection(
        titleFa = "خرج به تفکیک دسته",
        columns = listOf(
            ReportColumn("دسته", CellKind.TEXT, 30),
            ReportColumn("مبلغ (تومان)", CellKind.MONEY, 18),
            ReportColumn("سهم", CellKind.PERCENT, 10),
        ),
        rows = listOf(
            ReportRow(listOf(textCell("خرید"), moneyCell(400_000), percentCell(0.75))),
            ReportRow(listOf(textCell("تعداد"), countCell(3), textCell("—"))),
            ReportRow(
                listOf(textCell("جمع"), moneyCell(400_000), percentCell(1.0)),
                emphasis = true,
            ),
        ),
    )

    private fun document(title: String = "گزارش مالی") = ReportDocument(
        titleFa = title,
        periodFa = "مرداد ۱۴۰۵ · ۳۱ روز",
        metaFa = "ساخته‌شده در ۱۴۰۵/۰۵/۰۳ · ۶ تراکنش در بازه",
        footnoteFa = "فقط از تراکنش‌های ثبت‌شده ساخته شده؛ متن پیامک در آن نیست.",
        range = monthRange(1405, 5),
        sections = listOf(section, categorySection),
        generatedAt = 0L,
    )

    // ─────────────────────────── package structure ───────────────────────────

    @Test
    fun `required ooxml parts exist and are well-formed xml`() {
        val parts = unzip(XlsxWriter.write(document()))

        val required = listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml",
        )
        for (name in required) {
            assertTrue("missing part: $name", parts.containsKey(name))
            // Throws on malformed XML; that is the assertion.
            assertNotNull("unparseable part: $name", parse(parts.getValue(name)))
        }

        val contentTypes = parse(parts.getValue("[Content_Types].xml"))
        val partNames = contentTypes.getElementsByTagName("Override")
            .let { nodes -> (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("PartName") } }
        assertTrue(partNames.contains("/xl/workbook.xml"))
        assertTrue(partNames.contains("/xl/worksheets/sheet1.xml"))
        assertTrue(partNames.contains("/xl/styles.xml"))

        val workbook = parse(parts.getValue("xl/workbook.xml"))
        val sheets = workbook.getElementsByTagName("sheet")
        assertEquals(1, sheets.length)
        assertEquals("گزارش", (sheets.item(0) as Element).getAttribute("name"))

        val styles = parse(parts.getValue("xl/styles.xml"))
        val formats = styles.getElementsByTagName("numFmt")
            .let { nodes -> (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("formatCode") } }
        assertTrue(formats.contains("#,##0.#"))
        assertTrue(formats.contains("0.0%"))
    }

    @Test
    fun `worksheet is right to left and the first column is declared first`() {
        val parts = unzip(XlsxWriter.write(document()))
        val sheet = parse(parts.getValue("xl/worksheets/sheet1.xml"))

        val views = sheet.getElementsByTagName("sheetView")
        assertEquals(1, views.length)
        val view = views.item(0) as Element
        // This single attribute is what makes column A the rightmost column in Excel.
        assertEquals("1", view.getAttribute("rightToLeft"))

        val cells = cells(sheet)
        // The summary header is «شرح» then «مقدار»: declared A then B, rendered right then left.
        val labelHeader = cells.first { cellText(it) == "شرح" }
        val valueHeader = cells.first { cellText(it) == "مقدار" }
        assertTrue(labelHeader.getAttribute("r").startsWith("A"))
        assertTrue(valueHeader.getAttribute("r").startsWith("B"))

        val detailHeader = cells.first { cellText(it) == "دسته" }
        val shareHeader = cells.first { cellText(it) == "سهم" }
        assertTrue(detailHeader.getAttribute("r").startsWith("A"))
        assertTrue(shareHeader.getAttribute("r").startsWith("C"))
    }

    // ─────────────────────────── cells ───────────────────────────

    @Test
    fun `money percent and count columns are numeric cells with number formats`() {
        val parts = unzip(XlsxWriter.write(document()))
        val sheet = parse(parts.getValue("xl/worksheets/sheet1.xml"))
        val cells = cells(sheet)

        // 1,000,000 Rial is 100,000 Toman, and Excel gets the number, not the Persian string.
        val money = cells.first { cellValue(it) == "100000" }
        assertEquals("2", money.getAttribute("s"))
        assertEquals("", money.getAttribute("t")) // no t attribute: numeric

        val percent = cells.first { cellValue(it) == "0.75" }
        assertEquals("3", percent.getAttribute("s"))

        val count = cells.first { cellValue(it) == "3" && it.getAttribute("s") == "4" }
        assertNotNull(count)

        // Text cells are inline strings, so the Persian headers survive without a shared table.
        val textCell = cells.first { cellText(it) == "مبلغ (تومان)" }
        assertEquals("inlineStr", textCell.getAttribute("t"))
    }

    @Test
    fun `the total row is a formatted money cell, not a pasted string`() {
        val sheet = parse(unzip(XlsxWriter.write(document())).getValue("xl/worksheets/sheet1.xml"))
        val totalCells = cells(sheet).filter { it.getAttribute("s") == "5" }
        assertTrue("the emphasised money row survived", totalCells.any { cellValue(it) == "60000" })

        // The total row's «۱۰۰٪» cell keeps its percentage format; a bold General cell would
        // read 1 instead of 100%.
        val totalPercent = cells(sheet).first { it.getAttribute("s") == "10" }
        assertEquals("1", cellValue(totalPercent))
    }

    // ─────────────────────────── text safety ───────────────────────────

    @Test
    fun `xml special characters round-trip and control characters are dropped`() {
        val dirty = "خرید & فروش <تست> \"نقل‌قول\"" + '\u0000'
        val parts = unzip(XlsxWriter.write(document(title = dirty)))
        val sheet = parse(parts.getValue("xl/worksheets/sheet1.xml"))

        val title = cells(sheet).first { it.getAttribute("r") == "A1" }
        assertEquals("خرید & فروش <تست> \"نقل‌قول\"", cellText(title))
    }

    @Test
    fun `the same document produces identical bytes`() {
        val first = XlsxWriter.write(document())
        val second = XlsxWriter.write(document())
        // Fixed zip timestamps and deterministic ordering: a diff of two exports is meaningful.
        assertArrayEquals(first, second)
    }

    // ─────────────────────────── helpers ───────────────────────────

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun parse(bytes: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    private fun cells(sheet: Document): List<Element> {
        val nodes = sheet.getElementsByTagName("c")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun cellText(cell: Element): String {
        val nodes = cell.getElementsByTagName("t")
        return if (nodes.length == 0) "" else nodes.item(0).textContent
    }

    private fun cellValue(cell: Element): String {
        val nodes = cell.getElementsByTagName("v")
        return if (nodes.length == 0) "" else nodes.item(0).textContent
    }
}
