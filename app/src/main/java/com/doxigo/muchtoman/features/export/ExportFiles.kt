package com.doxigo.muchtoman.features.export

import com.doxigo.muchtoman.DAY_MS
import com.doxigo.muchtoman.JalaliDate
import com.doxigo.muchtoman.jalaliOf
import java.io.File
import java.util.Locale

/**
 * Where an exported report goes, what it is called, and how old files are cleaned up.
 *
 * Kept apart from the activity and from [ReportFormat] so it is plain file arithmetic that a JVM
 * test can exercise: the only reason this code could be wrong is a date formatted the wrong way
 * round, and that is exactly the kind of bug a phone hides until an accountant opens last
 * month's report looking for the wrong period.
 *
 * Everything lands in `cacheDir/exports/`, which is deliberate:
 *
 *  - the existing FileProvider declaration already maps the whole cache directory
 *    (`res/xml/file_paths.xml` → `<cache-path name="cache" path="." />`), so no manifest or XML
 *    change is needed for a new subdirectory — the same path `<cache>/muchtoman_ledger.csv`
 *    already uses;
 *  - a cache file is the right lifetime for a report: it is handed to a share target
 *    immediately, and Android may reclaim it later without the user losing anything;
 *  - a report is never written to external storage, so it does not linger in a gallery or a
 *    Downloads folder after the one conversation it was made for.
 */

/** `cacheDir/exports`, created if the phone has cleared the cache since the last run. */
fun exportDir(cacheDir: File): File = File(cacheDir, "exports").apply { mkdirs() }

/** The file name for [format], including its extension. */
fun exportFileName(range: DayRange, format: ReportFormat): String =
    exportFileNameFor(range, format.extension)

/**
 * A file name that survives being emailed, unzipped on Windows, and opened from a share sheet.
 *
 * ASCII only and no spaces: Persian file names are correct in principle and a pain in practice
 * on the other end, where the report is often a WhatsApp attachment opened on a laptop. The
 * Jalali dates keep it sortable and tell the accountant exactly which period it covers.
 */
fun exportFileNameFor(range: DayRange, extension: String): String {
    val from = jalaliOf(range.from)
    val to = jalaliOf(range.until - 1)
    return "chandtoman-report-${ymd(from)}_${ymd(to)}.$extension"
}

private fun ymd(date: JalaliDate): String =
    String.format(Locale.US, "%04d-%02d-%02d", date.year, date.month, date.day)

/**
 * Keeps the export folder from growing without bound.
 *
 * Cache files are evictable, but nothing guarantees the system will get to this directory, and
 * a household that exports monthly for a year would otherwise find twelve stale reports in the
 * share sheet's file list. A week is enough for "send it again"; older ones are not worth the
 * bytes. Failures are ignored — a report must never fail because cleanup could not run.
 */
fun pruneExports(dir: File, now: Long = System.currentTimeMillis(), maxAgeMs: Long = 7 * DAY_MS) {
    runCatching {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && now - file.lastModified() > maxAgeMs) file.delete()
        }
    }
}
