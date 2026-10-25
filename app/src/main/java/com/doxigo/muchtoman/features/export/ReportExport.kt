package com.doxigo.muchtoman.features.export

import android.content.Context

/**
 * The two files the export screen can produce.
 *
 * A format knows only how to name itself and which renderer to build. Where the bytes are
 * written, and how old ones are pruned, is [exportDir]/[exportFileName]/[pruneExports] in
 * `ExportFiles.kt` — plain file arithmetic that a JVM test can check.
 */
enum class ReportFormat(val fa: String, val extension: String, val mimeType: String) {
    XLSX(
        fa = "اکسل (XLSX)",
        extension = "xlsx",
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ),
    PDF(
        fa = "پی‌دی‌اف (PDF)",
        extension = "pdf",
        mimeType = "application/pdf",
    ),

    ;

    /**
     * The renderer for this format. XLSX is a pure-JVM object; PDF needs a `Context` for the
     * Persian typeface, which is the only reason this factory exists at all.
     */
    fun renderer(context: Context): ReportRenderer = when (this) {
        XLSX -> XlsxRenderer
        PDF -> PdfReportRenderer(context.applicationContext)
    }
}

/** The spreadsheet renderer as a named value, so [ReportFormat] can hand it out uniformly. */
object XlsxRenderer : ReportRenderer {
    override val extension: String get() = "xlsx"
    override val mimeType: String
        get() = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    override fun render(document: ReportDocument): ByteArray = XlsxWriter.write(document)
}
