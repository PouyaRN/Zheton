package com.doxigo.muchtoman

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetGlyphTest {

    private fun repoRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "settings.gradle.kts").exists()) cwd else cwd.parentFile ?: cwd
    }

    @Test
    fun `every static catalog asset and bank type resolves to a non-zero VectorDrawable`() {
        val allAssets = STATIC_CATALOG + BANK_TYPE + LEGACY_PARSIAN
        assertTrue("Catalog must not be empty", allAssets.isNotEmpty())
        for (asset in allAssets) {
            val resId = assetGlyph(asset)
            assertNotEquals("Asset ${asset.id} must resolve to a valid drawable resource", 0, resId)
        }
    }

    @Test
    fun `all 14 asset and flag VectorDrawable XML files exist and contain valid vector path data`() {
        val drawableDir = File(repoRoot(), "app/src/main/res/drawable")
        val requiredDrawables = listOf(
            "ic_asset_cash.xml",
            "ic_asset_bank.xml",
            "ic_asset_usd.xml",
            "ic_asset_eur.xml",
            "ic_asset_gbp.xml",
            "ic_asset_gold.xml",
            "ic_asset_silver.xml",
            "ic_asset_coin.xml",
            "ic_asset_stock.xml",
            "ic_asset_crypto.xml",
            "flag_no.xml",
            "flag_tr.xml",
            "flag_ae.xml",
            "flag_ca.xml",
        )
        for (fileName in requiredDrawables) {
            val file = File(drawableDir, fileName)
            assertTrue("Missing drawable file: $fileName", file.exists())
            val xml = file.readText(Charsets.UTF_8)
            assertTrue("$fileName must contain <vector>", xml.contains("<vector"))
            assertTrue("$fileName must contain android:pathData", xml.contains("android:pathData="))
        }
    }

    @Test
    fun `zero Unicode emoji literals remain in Android source files`() {
        val srcDir = File(repoRoot(), "app/src/main/java")
        val emojiRegex = Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{1F1E6}-\\x{1F1FF}]")
        val offenders = mutableListOf<String>()

        srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines(Charsets.UTF_8).forEachIndexed { idx, line ->
                    if (emojiRegex.containsMatchIn(line)) {
                        offenders.add("${file.relativeTo(srcDir)}:${idx + 1}: ${line.trim()}")
                    }
                }
            }

        assertTrue(
            "Found unexpected Unicode emojis in Android Kotlin sources:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }
}
