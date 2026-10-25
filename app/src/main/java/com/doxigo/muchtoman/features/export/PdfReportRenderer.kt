package com.doxigo.muchtoman.features.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.doxigo.muchtoman.R
import java.io.ByteArrayOutputStream

/**
 * Draws the laid-out report into a real PDF, in process, with no library.
 *
 * `PdfDocument` has been in the platform since API 19 and is the lightest thing that produces a
 * correct file: no iText, no font subsetting pipeline, no second copy of a rendering engine in
 * the APK. The trade is that text shaping is the platform's, which is exactly what we want —
 * Android already knows how to join Persian letters, and this app already ships Modam.
 *
 * The page arithmetic lives in [ReportLayout] and is unit-tested; this class only translates
 * `PlacedCell`s into `StaticLayout`s. It is not unit-tested because `android.graphics.pdf` is a
 * stub on the desktop JVM, and pretending otherwise would test the stub, not the report.
 */
class PdfReportRenderer(
    context: Context,
    private val geometry: PageGeometry = PageGeometry(),
    private val style: LayoutStyle = LayoutStyle(),
) : ReportRenderer {

    private val appContext: Context = context.applicationContext

    override val extension: String get() = "pdf"
    override val mimeType: String get() = "application/pdf"

    override fun render(document: ReportDocument): ByteArray {
        val pages = ReportLayout.paginate(document, geometry, style)
        val paints = Paints(regular, bold, style)
        val pdf = PdfDocument()
        try {
            for (page in pages) {
                val info = PdfDocument.PageInfo
                    .Builder(geometry.width.toInt(), geometry.height.toInt(), page.pageNumber)
                    .create()
                val pdfPage = pdf.startPage(info)
                drawPage(pdfPage.canvas, page, paints)
                pdf.finishPage(pdfPage)
            }
            val out = ByteArrayOutputStream(96 * 1024)
            pdf.writeTo(out)
            return out.toByteArray()
        } finally {
            // close() releases the native document even when a page failed midway; without it a
            // second export in the same process leaks the first one.
            pdf.close()
        }
    }

    /**
     * Modam, because the app's Persian is set in it. The fallback is the platform's default
     * face rather than a crash: a missing font should degrade a report, never prevent one.
     */
    private val regular: Typeface by lazy {
        runCatching { ResourcesCompat.getFont(appContext, R.font.modam) }.getOrNull() ?: Typeface.DEFAULT
    }

    private val bold: Typeface by lazy {
        runCatching { ResourcesCompat.getFont(appContext, R.font.modam_semibold) }.getOrNull() ?: regular
    }

    private fun drawPage(canvas: Canvas, page: ReportPage, paints: Paints) {
        // PdfDocument starts white, but a viewer that composites a page background would show
        // transparent text containers as grey; painting white makes the file self-contained.
        canvas.drawColor(Color.WHITE)

        page.header.forEach { drawCell(canvas, it, paints, availableHeight = 0f) }
        val headerRuleY = page.bodyTop - style.lineGap * 1.5f
        canvas.drawLine(geometry.contentLeft, headerRuleY, geometry.contentRight, headerRuleY, paints.rule)

        var bodyIndex = 0
        for (row in page.rows) {
            val isBody = row.cells.firstOrNull()?.role == TextRole.BODY
            if (isBody) {
                // Zebra striping for the figures only: a scanned page of numbers is one column
                // of ink, and a light band every other row is the cheapest way to keep the eye
                // on the line. Totals are left alone — they already have a rule above them.
                if (bodyIndex % 2 == 1 && !row.emphasis) {
                    canvas.drawRect(
                        geometry.contentLeft,
                        row.top,
                        geometry.contentRight,
                        row.top + row.height,
                        paints.zebra,
                    )
                }
                bodyIndex++
            }
            when (row.rule) {
                RowRule.TOP -> canvas.drawLine(
                    geometry.contentLeft, row.top, geometry.contentRight, row.top, paints.rule,
                )
                RowRule.BOTTOM -> canvas.drawLine(
                    geometry.contentLeft, row.top + row.height,
                    geometry.contentRight, row.top + row.height, paints.rule,
                )
                RowRule.NONE -> Unit
            }
            row.cells.forEach { drawCell(canvas, it, paints, availableHeight = row.height) }
        }

        val footerRuleY = page.footerTop - style.lineGap * 1.5f
        canvas.drawLine(geometry.contentLeft, footerRuleY, geometry.contentRight, footerRuleY, paints.rule)
        page.footer.forEach { drawCell(canvas, it, paints, availableHeight = 0f) }
    }

    /**
     * One cell, one line, right-aligned.
     *
     * `TextDirectionHeuristics.RTL` is what keeps a run like «−۱۲۳٬۴۵۶» or a latin brand name in
     * its intended place on a right-to-left line; without it the platform guesses from the first
     * strong character and a mixed row can reorder. Ellipsis rather than wrapping: the table's
     * columns are aligned by x, and a wrapped cell would push the figure under it out of line.
     */
    private fun drawCell(canvas: Canvas, cell: PlacedCell, paints: Paints, availableHeight: Float) {
        if (cell.text.isEmpty() || cell.width <= 0f) return
        val paint = paints.forRole(cell.role, cell.bold)
        val width = cell.width.toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(cell.text, 0, cell.text.length, paint, width)
            // ALIGN_NORMAL is the paragraph's *start*, and the paragraph direction is forced
            // RTL below — so normal is the right edge here. There is no ALIGN_RIGHT constant.
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val dy = if (availableHeight > 0f) ((availableHeight - layout.height) / 2f).coerceAtLeast(0f) else 0f
        canvas.save()
        canvas.translate(cell.left, cell.top + dy)
        layout.draw(canvas)
        canvas.restore()
    }

    /** Paints are created once per export; rebuilding a `TextPaint` per cell is measurable. */
    private class Paints(
        private val regular: Typeface,
        private val bold: Typeface,
        // Not named `style`: `TextPaint` already exposes a `style` property, and inside the
        // `apply` block below that one wins the name — which reads as "no titleSize" at compile
        // time and is a genuinely confusing error.
        private val layoutStyle: LayoutStyle,
    ) {
        private val cache = HashMap<Pair<TextRole, Boolean>, TextPaint>(16)

        val rule = Paint().apply {
            color = 0xFF9DB3AC.toInt()
            strokeWidth = 0.6f
            isAntiAlias = true
        }

        val zebra = Paint().apply {
            color = 0xFFF4F7F6.toInt()
            isAntiAlias = false
        }

        fun forRole(role: TextRole, boldText: Boolean): TextPaint =
            cache.getOrPut(role to boldText) {
                TextPaint().apply {
                    isAntiAlias = true
                    color = 0xFF16211D.toInt()
                    typeface = if (boldText) bold else regular
                    textSize = when (role) {
                        TextRole.TITLE -> layoutStyle.titleSize
                        TextRole.SUBTITLE -> layoutStyle.subtitleSize
                        TextRole.META -> layoutStyle.metaSize
                        TextRole.SECTION -> layoutStyle.sectionSize
                        TextRole.TABLE_HEADER -> layoutStyle.headerSize
                        TextRole.BODY -> layoutStyle.cellSize
                        TextRole.FOOTER -> layoutStyle.metaSize
                    }
                }
            }
    }
}
