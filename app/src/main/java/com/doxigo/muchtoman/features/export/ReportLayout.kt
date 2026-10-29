package com.doxigo.muchtoman.features.export

import com.doxigo.muchtoman.faNumber

/**
 * Where every printed string lands on an A4 page — computed without a Canvas.
 *
 * The split exists for one reason: pagination and right-to-left column order are the parts of
 * PDF generation that can be wrong in ways nobody notices until an accountant reads the file.
 * By reducing them to arithmetic over the [ReportDocument], a JVM test can assert that the first
 * column really is the rightmost one and that no row crosses the bottom margin. [PdfReportRenderer]
 * is then a thin drawing loop that cannot silently change the layout.
 *
 * All measurements are PostScript points (1/72 inch), which is also what `PdfDocument` takes.
 * Rows are single lines by design: a money column that wraps loses its alignment against the
 * figure above it, and a report that has to be re-read to be trusted has already failed.
 */

/** Paper and margins. A4 is what Iranian printers and accountants expect. */
data class PageGeometry(
    val width: Float = 595f,
    val height: Float = 842f,
    val marginX: Float = 40f,
    val marginY: Float = 42f,
) {
    val contentLeft: Float get() = marginX
    val contentRight: Float get() = width - marginX
    val contentWidth: Float get() = contentRight - contentLeft
}

/** Type sizes and spacing, in points. Kept as data so the tests can use the same numbers. */
data class LayoutStyle(
    val titleSize: Float = 18f,
    val subtitleSize: Float = 11f,
    val metaSize: Float = 8.5f,
    val sectionSize: Float = 12f,
    val headerSize: Float = 9f,
    val cellSize: Float = 9f,
    val rowPadding: Float = 5f,
    val sectionGap: Float = 12f,
    val lineGap: Float = 4f,
)

/** What a placed string is, which is how the renderer picks its paint. */
enum class TextRole { TITLE, SUBTITLE, META, SECTION, TABLE_HEADER, BODY, FOOTER }

/** A horizontal rule the renderer should draw at the row's top or bottom edge. */
enum class RowRule { NONE, TOP, BOTTOM }

/** One string, already positioned and sized as a width. */
data class PlacedCell(
    val text: String,
    val left: Float,
    val width: Float,
    val top: Float,
    val role: TextRole,
    val bold: Boolean = false,
) {
    val right: Float get() = left + width
}

/** One line of the page: a table row, a section title, or a note. */
data class PlacedRow(
    val cells: List<PlacedCell>,
    val top: Float,
    val height: Float,
    val emphasis: Boolean = false,
    val rule: RowRule = RowRule.NONE,
)

/** A finished page. Page numbers are stamped after pagination, so they are always correct. */
data class ReportPage(
    val pageNumber: Int,
    val pageCount: Int,
    val header: List<PlacedCell>,
    val rows: List<PlacedRow>,
    val footer: List<PlacedCell>,
    val bodyTop: Float,
    val bodyBottom: Float,
    val footerTop: Float,
)

object ReportLayout {

    /** Paginates [document]. Deterministic: same document and style, same pages. */
    fun paginate(
        document: ReportDocument,
        geometry: PageGeometry = PageGeometry(),
        style: LayoutStyle = LayoutStyle(),
    ): List<ReportPage> = Paginator(document, geometry, style).pages()

    private class Paginator(
        private val document: ReportDocument,
        private val geometry: PageGeometry,
        private val style: LayoutStyle,
    ) {
        private val contentLeft = geometry.contentLeft
        private val contentRight = geometry.contentRight
        private val contentWidth = geometry.contentWidth

        private val titleTop = geometry.marginY
        private val subtitleTop = titleTop + style.titleSize * 1.25f
        private val metaTop = subtitleTop + style.subtitleSize * 1.4f
        private val bodyTop = metaTop + style.metaSize * 1.4f + style.lineGap * 2f
        private val footerTop = geometry.height - geometry.marginY - style.metaSize * 1.4f
        private val bodyBottom = footerTop - style.lineGap * 3f

        private val bodyRowHeight = style.cellSize * 1.6f + style.rowPadding * 2f
        private val headerRowHeight = style.headerSize * 1.6f + style.rowPadding * 2f
        private val sectionTitleHeight = style.sectionSize * 1.6f
        private val noteHeight = style.metaSize * 1.6f + style.rowPadding * 2f

        fun pages(): List<ReportPage> {
            val finished = mutableListOf<List<PlacedRow>>()
            var rows = mutableListOf<PlacedRow>()
            var y = bodyTop

            fun flush() {
                finished += rows
                rows = mutableListOf()
                y = bodyTop
            }

            fun tableHeader(widths: List<Float>, columns: List<ReportColumn>) {
                rows += placedRow(
                    widths = widths,
                    cells = columns.map { ReportCell(it.headerFa) },
                    top = y,
                    height = headerRowHeight,
                    role = TextRole.TABLE_HEADER,
                    bold = true,
                    emphasis = false,
                    rule = RowRule.BOTTOM,
                )
                y += headerRowHeight
            }

            for (section in document.sections) {
                val hasTable = section.columns.isNotEmpty()
                val widths = if (hasTable) widthsOf(section.columns) else emptyList()
                val tableHeaderHeight = if (hasTable) headerRowHeight else 0f
                val firstRowHeight = when {
                    section.rows.isNotEmpty() -> bodyRowHeight
                    section.noteFa.isNotBlank() -> noteHeight
                    else -> 0f
                }

                // Title, column headers and one row move together; a header stranded at the
                // bottom of a page is a table nobody can read.
                if (y + sectionTitleHeight + tableHeaderHeight + firstRowHeight > bodyBottom && rows.isNotEmpty()) {
                    flush()
                }

                rows += fullRow(section.titleFa, TextRole.SECTION, bold = true, top = y, height = sectionTitleHeight)
                y += sectionTitleHeight

                if (hasTable) tableHeader(widths, section.columns)

                for (row in section.rows) {
                    if (y + bodyRowHeight > bodyBottom && rows.isNotEmpty()) {
                        flush()
                        // Repeat the column headers on the new page. The section title is left
                        // behind on purpose: the reader already knows which table this is, and
                        // repeating it would push another row off every page.
                        if (hasTable) tableHeader(widths, section.columns)
                    }
                    rows += placedRow(
                        widths = widths,
                        cells = row.cells,
                        top = y,
                        height = bodyRowHeight,
                        role = TextRole.BODY,
                        bold = row.emphasis,
                        emphasis = row.emphasis,
                        rule = if (row.emphasis) RowRule.TOP else RowRule.NONE,
                    )
                    y += bodyRowHeight
                }

                if (section.rows.isEmpty() && section.noteFa.isNotBlank()) {
                    if (y + noteHeight > bodyBottom && rows.isNotEmpty()) flush()
                    rows += fullRow(section.noteFa, TextRole.META, bold = false, top = y, height = noteHeight)
                    y += noteHeight
                }

                y += style.sectionGap
            }

            // The footnote is the report's statement about what it leaves out; it belongs at the
            // end of the last page, not in a page footer where it would repeat itself.
            if (document.footnoteFa.isNotBlank()) {
                if (y + noteHeight > bodyBottom && rows.isNotEmpty()) flush()
                rows += fullRow(document.footnoteFa, TextRole.META, bold = false, top = y, height = noteHeight)
                y += noteHeight
            }

            if (rows.isNotEmpty() || finished.isEmpty()) flush()

            val pageCount = finished.size.coerceAtLeast(1)
            return finished.mapIndexed { index, pageRows ->
                val number = index + 1
                ReportPage(
                    pageNumber = number,
                    pageCount = pageCount,
                    header = headerCells(number),
                    rows = pageRows,
                    footer = footerCells(number, pageCount),
                    bodyTop = bodyTop,
                    bodyBottom = bodyBottom,
                    footerTop = footerTop,
                )
            }
        }

        /**
         * Column widths from their requested character counts.
         *
         * The last column absorbs the rounding remainder, so the table still ends exactly on the
         * left margin instead of a fraction of a point short of it — which is the kind of drift
         * that makes a printed table look hand-drawn.
         */
        private fun widthsOf(columns: List<ReportColumn>): List<Float> {
            if (columns.isEmpty()) return emptyList()
            val total = columns.sumOf { it.widthChars }.coerceAtLeast(1)
            val raw = columns.map { contentWidth * it.widthChars / total }
            if (raw.size == 1) return listOf(contentWidth)
            val used = raw.dropLast(1).sum()
            return raw.dropLast(1) + (contentWidth - used)
        }

        /**
         * Places [cells] right to left: the first column starts at the right margin and each
         * following column begins where the previous one ended. This is the RTL ordering the
         * layout test pins down — without it the figures would sit to the left of their labels.
         */
        private fun placedRow(
            widths: List<Float>,
            cells: List<ReportCell>,
            top: Float,
            height: Float,
            role: TextRole,
            bold: Boolean,
            emphasis: Boolean,
            rule: RowRule,
        ): PlacedRow {
            var right = contentRight
            val placed = cells.mapIndexed { index, cell ->
                val width = widths.getOrElse(index) { 0f }
                val left = right - width
                right = left
                PlacedCell(cell.text, left, width, top, role, bold)
            }
            return PlacedRow(placed, top, height, emphasis = emphasis, rule = rule)
        }

        private fun fullRow(text: String, role: TextRole, bold: Boolean, top: Float, height: Float): PlacedRow =
            PlacedRow(
                cells = listOf(PlacedCell(text, contentLeft, contentWidth, top, role, bold)),
                top = top,
                height = height,
            )

        private fun headerCells(pageNumber: Int): List<PlacedCell> = buildList {
            add(PlacedCell(document.titleFa, contentLeft, contentWidth, titleTop, TextRole.TITLE, bold = true))
            add(PlacedCell(document.periodFa, contentLeft, contentWidth, subtitleTop, TextRole.SUBTITLE))
            // The generation line is only on the first page; repeating it on every page would
            // spend the reader's attention on a fact that does not change.
            if (pageNumber == 1) {
                add(PlacedCell(document.metaFa, contentLeft, contentWidth, metaTop, TextRole.META))
            }
        }

        private fun footerCells(pageNumber: Int, pageCount: Int): List<PlacedCell> {
            val half = contentWidth / 2f
            return listOf(
                PlacedCell(
                    "صفحه ${faNumber(pageNumber)} از ${faNumber(pageCount)}",
                    contentLeft,
                    half,
                    footerTop,
                    TextRole.FOOTER,
                ),
                PlacedCell("چندتومان · گزارش خودکار", contentLeft + half, half, footerTop, TextRole.FOOTER),
            )
        }
    }
}
