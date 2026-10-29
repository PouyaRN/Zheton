package com.doxigo.muchtoman.features.export

import com.doxigo.muchtoman.DAY_MS
import com.doxigo.muchtoman.jalaliDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * File naming and cleanup, which is the only part of the export worth testing without a device:
 * a report called by the wrong dates is one an accountant opens expecting a different month.
 */
class ExportFilesTest {

    @Test
    fun `a whole month is named by both of its Jalali ends`() {
        val range = monthRange(1405, 5)
        // ASCII, sortable, no spaces: the file travels through WhatsApp and Windows Explorer.
        assertEquals("chandtoman-report-1405-05-01_1405-05-31.pdf", exportFileNameFor(range, "pdf"))
        assertEquals("chandtoman-report-1405-05-01_1405-05-31.xlsx", exportFileNameFor(range, "xlsx"))
    }

    @Test
    fun `the end date is the last day inside the half-open range`() {
        // until is exclusive, so a range that stops before the 31st must not claim the 31st.
        val range = DayRange(jalaliDay(1405, 5, 1), jalaliDay(1405, 5, 31))
        assertEquals("chandtoman-report-1405-05-01_1405-05-30.pdf", exportFileNameFor(range, "pdf"))
    }

    @Test
    fun `the export directory is created under the cache directory`() {
        val cache = Files.createTempDirectory("chandtoman-cache").toFile()
        try {
            val dir = exportDir(cache)
            assertTrue(dir.isDirectory)
            assertEquals("exports", dir.name)
            assertEquals(cache.canonicalFile, dir.parentFile?.canonicalFile)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun `pruning drops old reports and keeps the fresh ones`() {
        val dir = Files.createTempDirectory("chandtoman-exports").toFile()
        try {
            val now = 1_800_000_000_000L
            val old = File(dir, "old.xlsx").apply {
                writeText("x")
                setLastModified(now - 8 * DAY_MS)
            }
            val fresh = File(dir, "fresh.pdf").apply {
                writeText("x")
                setLastModified(now - 2 * DAY_MS)
            }

            pruneExports(dir, now = now)

            assertFalse("an eight-day-old report is past the week", old.exists())
            assertTrue("a report from two days ago must survive", fresh.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
