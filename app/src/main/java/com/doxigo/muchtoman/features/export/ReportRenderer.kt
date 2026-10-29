package com.doxigo.muchtoman.features.export

/**
 * The one seam between the report and a file format.
 *
 * It exists so the decision "what goes in the report" never depends on "how it is drawn". The
 * XLSX side is pure JVM code; the PDF side needs `android.graphics.pdf`, which unit tests cannot
 * load. Keeping both behind this interface means the activity can offer a choice without a
 * `when` that reaches into either renderer, and a test double can stand in for the PDF one.
 */
interface ReportRenderer {
    /** File extension without the dot: `xlsx`, `pdf`. */
    val extension: String

    /** MIME type for the share intent. */
    val mimeType: String

    /** Renders [document] and returns the complete file contents. */
    fun render(document: ReportDocument): ByteArray
}
