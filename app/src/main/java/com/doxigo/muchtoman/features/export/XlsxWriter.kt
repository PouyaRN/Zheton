package com.doxigo.muchtoman.features.export

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Style ids into <cellXfs> above. Kept as constants so a cell's look is one readable name.
private const val S_TEXT = 0
private const val S_HEADER = 1
private const val S_MONEY = 2
private const val S_PERCENT = 3
private const val S_COUNT = 4
private const val S_TOTAL_MONEY = 5
private const val S_TITLE = 6
private const val S_SECTION = 7
private const val S_TOTAL_TEXT = 8
private const val S_META = 9
private const val S_TOTAL_PERCENT = 10

/** Narrowest column, in Excel character units, before a section's own request is applied. */
private const val MIN_WIDTH = 10

/**
 * A real `.xlsx` written with nothing but `java.util.zip`.
 *
 * The app ships without a spreadsheet library on purpose: an OOXML workbook is a zip of a
 * handful of XML parts, and a report writer that fits in one file is easier to audit than a
 * dependency that pulls a charting engine along with it. No Gradle change, no new bytes in the
 * APK, and the same code runs on a laptop JVM for the tests.
 *
 * Choices that are not accidental:
 *
 *  - **Strings are inline (`t="inlineStr"`), not a shared-string table.** The optional
 *    `sharedStrings.xml` part exists to deduplicate large sheets; this one is a few hundred
 *    cells written once, and skipping the table removes an index that can disagree with itself.
 *  - **The sheet is right-to-left (`rightToLeft="1"`).** Column A is then the rightmost column,
 *    which is how the rows below were ordered. Without it, «دسته» would appear on the left of
 *    the figures it labels.
 *  - **Figures are numeric cells.** A money column pasted into a spreadsheet has to be a column
 *    of numbers, not of formatted Persian strings, or it cannot be summed — which is the whole
 *    reason to ask for Excel rather than a PDF. Persian digits belong to the PDF.
 *  - **No zip timestamps.** Entries are stamped 1980-01-01 so two exports of identical data are
 *    byte-identical, which is what lets a test compare structure instead of noise.
 */
object XlsxWriter {

    /** The whole workbook, as bytes ready to be written to a file or an intent stream. */
    fun write(document: ReportDocument): ByteArray {
        val sheet = SheetBuilder(document).build()
        val out = ByteArrayOutputStream(16 * 1024)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(entry("[Content_Types].xml"))
            zip.write(CONTENT_TYPES.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(entry("_rels/.rels"))
            zip.write(ROOT_RELS.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(entry("xl/workbook.xml"))
            zip.write(workbookXml().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(entry("xl/_rels/workbook.xml.rels"))
            zip.write(WORKBOOK_RELS.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(entry("xl/styles.xml"))
            zip.write(STYLES.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(entry("xl/worksheets/sheet1.xml"))
            zip.write(sheet.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun entry(name: String) = ZipEntry(name).apply { time = 0L }

    private fun workbookXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<workbookPr/>
<sheets><sheet name="گزارش" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    /**
     * Tahoma, not the default Calibri: every target (Excel, LibreOffice, WPS, Google Sheets)
     * has a Persian-capable face under that name, and Calibri does not carry Persian glyphs on
     * a machine without the Windows language pack. The colours are print-safe rather than the
     * app's dark theme: a report is read on white paper and in another person's spreadsheet.
     */
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2">
<numFmt numFmtId="164" formatCode="#,##0.#"/>
<numFmt numFmtId="165" formatCode="0.0%"/>
</numFmts>
<fonts count="4">
<font><sz val="11"/><color rgb="FF16211D"/><name val="Tahoma"/></font>
<font><b/><sz val="11"/><color rgb="FF16211D"/><name val="Tahoma"/></font>
<font><b/><sz val="15"/><color rgb="FF16211D"/><name val="Tahoma"/></font>
<font><sz val="9"/><color rgb="FF5C7069"/><name val="Tahoma"/></font>
</fonts>
<fills count="3">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFE8F0ED"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="3">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left/><right/><top style="thin"><color rgb="FF9DB3AC"/></top><bottom/><diagonal/></border>
<border><left/><right/><top/><bottom style="thin"><color rgb="FF9DB3AC"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="11">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf>
<xf numFmtId="0" fontId="1" fillId="2" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1" readingOrder="2"/></xf>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
<xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
<xf numFmtId="164" fontId="1" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf>
<xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="right" vertical="center" wrapText="1" readingOrder="2"/></xf>
<xf numFmtId="165" fontId="1" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    /**
     * The one sheet, row by row. Section titles are plain rows rather than separate sheets: an
     * accountant reading a family's month wants to scroll, not to click between tabs.
     */
    private class SheetBuilder(private val document: ReportDocument) {
        private val rows = StringBuilder()
        private val widths = mutableListOf<Int>()
        private var rowNumber = 0
        private var columnCount = 1

        fun build(): String {
            textRow(document.titleFa, S_TITLE)
            textRow(document.periodFa, S_TEXT)
            textRow(document.metaFa, S_META)
            blankRow()

            for (section in document.sections) {
                textRow(section.titleFa, S_SECTION)
                if (section.columns.isNotEmpty()) {
                    headerRow(section.columns)
                    section.columns.forEachIndexed { index, column ->
                        while (widths.size <= index) widths += MIN_WIDTH
                        widths[index] = maxOf(widths[index], column.widthChars)
                    }
                }
                for (row in section.rows) {
                    writeRow(row.cells.map { it to styleFor(it, row.emphasis) })
                }
                if (section.noteFa.isNotBlank()) noteRow(section.noteFa)
                blankRow()
            }

            if (document.footnoteFa.isNotBlank()) noteRow(document.footnoteFa)

            return worksheetXml()
        }

        private fun textRow(text: String, style: Int) = writeRow(listOf(textCell(text) to style))

        /**
         * A note row grows to fit. Its style wraps text but Excel only auto-fits a row that is
         * allowed to grow, so a long footnote would otherwise open as one clipped line in a
         * 15-point row. Thirty characters is the width of column A in this sheet.
         */
        private fun noteRow(text: String) {
            val lines = (text.length / 30 + 1).coerceIn(1, 8)
            writeRow(listOf(textCell(text) to S_META), heightLines = lines)
        }

        private fun headerRow(columns: List<ReportColumn>) =
            writeRow(columns.map { ReportCell(it.headerFa) to S_HEADER })

        private fun blankRow() {
            rowNumber++
            rows.append("<row r=\"").append(rowNumber).append("\"/>")
        }

        private fun writeRow(cells: List<Pair<ReportCell, Int>>, heightLines: Int = 1) {
            rowNumber++
            columnCount = maxOf(columnCount, cells.size)
            if (heightLines > 1) {
                // 15 points per wrapped line, the same unit as <sheetFormatPr defaultRowHeight>.
                rows.append("<row r=\"").append(rowNumber)
                    .append("\" ht=\"").append(heightLines * 15)
                    .append("\" customHeight=\"1\">")
            } else {
                rows.append("<row r=\"").append(rowNumber).append("\">")
            }
            cells.forEachIndexed { index, (cell, style) ->
                val ref = colName(index) + rowNumber
                rows.append("<c r=\"").append(ref).append("\" s=\"").append(style).append('"')
                if (cell.kind == CellKind.TEXT) {
                    rows.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escapeXml(cell.text))
                        .append("</t></is></c>")
                } else {
                    rows.append("><v>").append(numberText(cell.number ?: 0.0)).append("</v></c>")
                }
            }
            rows.append("</row>")
        }

        private fun worksheetXml(): String {
            val dimension = "A1:${colName(columnCount - 1)}$rowNumber"
            return buildString(rows.length + 1024) {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                append("<dimension ref=\"").append(dimension).append("\"/>")
                // The single most important line in this file: A is the rightmost column.
                append("<sheetViews><sheetView rightToLeft=\"1\" tabSelected=\"1\" workbookViewId=\"0\"/></sheetViews>")
                append("<sheetFormatPr defaultRowHeight=\"15\"/>")
                append("<cols>")
                widths.forEachIndexed { index, chars ->
                    val n = index + 1
                    val width = chars + 2
                    append("<col min=\"").append(n).append("\" max=\"").append(n)
                        .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>")
                }
                append("</cols>")
                append("<sheetData>").append(rows).append("</sheetData>")
                append("<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>")
                append("</worksheet>")
            }
        }

        private fun styleFor(cell: ReportCell, emphasis: Boolean): Int = when {
            emphasis && cell.kind == CellKind.MONEY -> S_TOTAL_MONEY
            // A total percentage is still a percentage; giving it the plain bold text style
            // would print 1 instead of 100%.
            emphasis && cell.kind == CellKind.PERCENT -> S_TOTAL_PERCENT
            emphasis -> S_TOTAL_TEXT
            else -> when (cell.kind) {
                CellKind.TEXT -> S_TEXT
                CellKind.MONEY -> S_MONEY
                CellKind.PERCENT -> S_PERCENT
                CellKind.COUNT -> S_COUNT
            }
        }

        private fun colName(index: Int): String {
            var n = index
            val sb = StringBuilder(2)
            while (true) {
                sb.append('A' + n % 26)
                n = n / 26 - 1
                if (n < 0) break
            }
            return sb.reverse().toString()
        }
    }

}

/**
 * Excel stores numbers in the file's invariant form, and rejects the scientific notation a plain
 * `Double.toString` produces for a large Toman figure. `toPlainString` avoids both that and a
 * locale-dependent decimal comma.
 */
private fun numberText(value: Double): String {
    if (!value.isFinite()) return "0"
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

/**
 * XML 1.0 forbids most control characters outright, and a single one anywhere in a cell makes
 * the whole workbook unopenable rather than merely ugly. Names come from her keyboard and could
 * in principle carry one, so they are stripped rather than trusted.
 */
private fun escapeXml(text: String): String {
    val sb = StringBuilder(text.length + 16)
    for (c in text) {
        when {
            c == '&' -> sb.append("&amp;")
            c == '<' -> sb.append("&lt;")
            c == '>' -> sb.append("&gt;")
            c == '"' -> sb.append("&quot;")
            c == '\'' -> sb.append("&apos;")
            c == '\n' || c == '\r' || c == '\t' || c >= ' ' -> sb.append(c)
            else -> Unit
        }
    }
    return sb.toString()
}
