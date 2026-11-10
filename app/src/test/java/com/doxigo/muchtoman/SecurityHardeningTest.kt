package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

private fun resolveAppDir(): File {
    val candidates = listOf(File("."), File("app"))
    return candidates.firstOrNull { File(it, "src/main/AndroidManifest.xml").exists() }
        ?: error("Cannot locate app module directory from ${File(".").absolutePath}")
}

private fun resolveRepoRoot(): File {
    val appDir = resolveAppDir().canonicalFile
    return if (appDir.name == "app") appDir.parentFile else appDir
}

private fun parseXml(file: File) =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)

class ManifestSecurityTest {

    private val manifestFile = File(resolveAppDir(), "src/main/AndroidManifest.xml")
    private val doc by lazy { parseXml(manifestFile) }

    @Test
    fun `application disables cleartext traffic, disables cloud backup, sets network security config, and is not debuggable`() {
        val appNodes = doc.getElementsByTagName("application")
        assertEquals(1, appNodes.length)
        val app = appNodes.item(0) as Element

        assertEquals("false", app.getAttributeNS(ANDROID_NS, "usesCleartextTraffic"))
        assertEquals("false", app.getAttributeNS(ANDROID_NS, "allowBackup"))
        assertEquals("@xml/network_security_config", app.getAttributeNS(ANDROID_NS, "networkSecurityConfig"))
        assertFalse(
            "application must never hardcode android:debuggable=true",
            app.getAttributeNS(ANDROID_NS, "debuggable") == "true",
        )
    }

    @Test
    fun `unverified domain sync_zheton_app does not set autoVerify true`() {
        val intentFilters = doc.getElementsByTagName("intent-filter")
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            val autoVerify = filter.getAttributeNS(ANDROID_NS, "autoVerify")
            val dataNodes = filter.getElementsByTagName("data")
            for (j in 0 until dataNodes.length) {
                val data = dataNodes.item(j) as Element
                val host = data.getAttributeNS(ANDROID_NS, "host")
                if (host == "sync.zheton.app") {
                    assertFalse(
                        "sync.zheton.app must not use autoVerify=true while domain is unprovisioned",
                        autoVerify == "true",
                    )
                }
            }
        }
    }

    @Test
    fun `exported components match the reviewed allowlist`() {
        val expected = File(resolveAppDir(), "src/test/resources/exported-components.txt")
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .sorted()

        val componentTags = listOf("activity", "activity-alias", "service", "receiver", "provider")
        val actual = mutableListOf<String>()
        for (tag in componentTags) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                if (el.getAttributeNS(ANDROID_NS, "exported") == "true") {
                    val name = el.getAttributeNS(ANDROID_NS, "name")
                    val perm = el.getAttributeNS(ANDROID_NS, "permission")
                    val entry = if (perm.isNotBlank()) "$tag:$name:$perm" else "$tag:$name"
                    actual += entry
                }
            }
        }
        assertEquals(expected, actual.sorted())
    }

    @Test
    fun `permissions match the reviewed allowlist`() {
        val expected = File(resolveAppDir(), "src/test/resources/manifest-permissions.txt")
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .sorted()

        val nodes = doc.getElementsByTagName("uses-permission")
        val actual = (0 until nodes.length)
            .map { (nodes.item(it) as Element).getAttributeNS(ANDROID_NS, "name") }
            .sorted()

        assertEquals(expected, actual)
    }
}

class BackupRulesTest {

    @Test
    fun `backup_rules and data_extraction_rules do not expose financial databases to cloud backup`() {
        val appDir = resolveAppDir()
        val backupRulesFile = File(appDir, "src/main/res/xml/backup_rules.xml")
        val extractionRulesFile = File(appDir, "src/main/res/xml/data_extraction_rules.xml")
        assertTrue(backupRulesFile.exists())
        assertTrue(extractionRulesFile.exists())

        val backupDoc = parseXml(backupRulesFile)
        val includes = backupDoc.getElementsByTagName("include")
        assertEquals("backup_rules.xml must not include sensitive files in cloud backup", 0, includes.length)

        val extractionDoc = parseXml(extractionRulesFile)
        val cloudBackups = extractionDoc.getElementsByTagName("cloud-backup")
        for (i in 0 until cloudBackups.length) {
            val cloud = cloudBackups.item(i) as Element
            val cloudIncludes = cloud.getElementsByTagName("include")
            assertEquals("cloud-backup must not include sensitive files", 0, cloudIncludes.length)
        }
    }

    @Test
    fun `network_security_config forbids cleartext traffic`() {
        val netSecFile = File(resolveAppDir(), "src/main/res/xml/network_security_config.xml")
        assertTrue("network_security_config.xml must exist", netSecFile.exists())
        val doc = parseXml(netSecFile)
        val baseConfigs = doc.getElementsByTagName("base-config")
        assertEquals(1, baseConfigs.length)
        val baseConfig = baseConfigs.item(0) as Element
        assertEquals("false", baseConfig.getAttribute("cleartextTrafficPermitted"))
    }
}

class NoSensitiveLogTest {

    private val sensitiveLogRegex = Regex(
        """Log\.[divew]\s*\([^)]*(total=\$|\bamount=\$|\bbalance=\$|\bfold=\$|\bderived=\$|\bfoldedRial\b|\bbalance\.rial\b|\btotalRial\b|\binstallmentRial\b|\bfullPan\b|\bsheba\b|m\.body)""",
    )

    @Test
    fun `no Kotlin source file logs financial amounts, balances, card PANs, or SMS bodies`() {
        val srcRoot = File(resolveAppDir(), "src/main/java")
        val violations = mutableListOf<String>()
        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                sensitiveLogRegex.findAll(text).forEach { match ->
                    violations += "${file.relativeTo(srcRoot)}: ${match.value.take(100)}"
                }
            }
        assertTrue("Sensitive data found in Log calls:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}

class DeepLinkValidationTest {

    @Test
    fun `sanitizeQuickAddCategoryId accepts valid category ids and rejects malicious or malformed inputs`() {
        assertEquals("cat_food", sanitizeQuickAddCategoryId("cat_food"))
        assertEquals("cat_user_0195f3a0-1234", sanitizeQuickAddCategoryId("  cat_user_0195f3a0-1234  "))

        assertNull(sanitizeQuickAddCategoryId(null))
        assertNull(sanitizeQuickAddCategoryId(""))
        assertNull(sanitizeQuickAddCategoryId("   "))
        assertNull(sanitizeQuickAddCategoryId("javascript:alert(1)"))
        assertNull(sanitizeQuickAddCategoryId("cat_food; DROP TABLE manual_txns;"))
        assertNull(sanitizeQuickAddCategoryId("cat_food\u0000evil"))
        assertNull(sanitizeQuickAddCategoryId("cat_" + "a".repeat(80)))
        assertNull(sanitizeQuickAddCategoryId("../etc/passwd"))
    }

    @Test
    fun `parsePairingLink validates scheme, https base URL, hid, code, scope, and 32-byte key`() {
        val validKey = b64Url(ByteArray(32) { (it + 1).toByte() })
        val hid = "0123456789abcdef0123456789abcdef"
        val validLink =
            "zheton://join#url=https%3A%2F%2Fsync.zheton.app&hid=$hid&pair=code123456&scope=family%3A$hid&k=$validKey"

        val parsed = parsePairingLink(validLink)
        assertNotNull("Expected valid pairing link to parse", parsed)
        assertEquals("https://sync.zheton.app", parsed!!.base)
        assertEquals(hid, parsed.hid)
        assertEquals("code123456", parsed.code)
        assertEquals("family:$hid", parsed.scope)
        assertEquals(32, parsed.key.size)

        // Reject cleartext http base URL
        assertNull(
            parsePairingLink(
                "zheton://join#url=http%3A%2F%2Fsync.zheton.app&hid=$hid&pair=code123456&scope=family%3A$hid&k=$validKey",
            ),
        )
        // Reject wrong scheme
        assertNull(
            parsePairingLink(
                "javascript://join#url=https%3A%2F%2Fsync.zheton.app&hid=$hid&pair=code123456&scope=family%3A$hid&k=$validKey",
            ),
        )
        // Reject non-hex hid / path traversal
        assertNull(
            parsePairingLink(
                "zheton://join#url=https%3A%2F%2Fsync.zheton.app&hid=../../etc/passwd1234&pair=code123456&scope=family%3A$hid&k=$validKey",
            ),
        )
        // Reject wrong key length (16 bytes instead of 32)
        val shortKey = b64Url(ByteArray(16) { 1 })
        assertNull(
            parsePairingLink(
                "zheton://join#url=https%3A%2F%2Fsync.zheton.app&hid=$hid&pair=code123456&scope=family%3A$hid&k=$shortKey",
            ),
        )
        // Reject overly long link
        assertNull(parsePairingLink(validLink + "a".repeat(1000)))
        // Reject control characters
        assertNull(parsePairingLink("$validLink\u0007"))
    }
}

class RepoHygieneTest {

    @Test
    fun `git tracked files contain no non-product artifacts, keystores, or walkthrough dumps`() {
        val root = resolveRepoRoot()
        val proc = ProcessBuilder("git", "ls-files")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val files = proc.inputStream.bufferedReader().readLines()
        assertEquals(0, proc.waitFor())

        val forbidden = files.filter { path ->
            path.startsWith(".aistudio/") ||
                path.startsWith("public/assets/aistudio/") ||
                path.startsWith("pwa/public/assets/aistudio/") ||
                path.startsWith("docs/screenshots/walkthrough/") ||
                path.endsWith(".jks") ||
                path.endsWith(".keystore") ||
                path.endsWith(".p12") ||
                path.endsWith(".key") ||
                path == "keystore.properties" ||
                (path.startsWith("pwa/public/") && path.endsWith(".zip")) ||
                (path.startsWith("public/") && path.endsWith(".zip"))
        }
        assertTrue("Forbidden tracked files found:\n${forbidden.joinToString("\n")}", forbidden.isEmpty())
    }

    @Test
    fun `test files do not contain real-range personal 0912 mobile numbers`() {
        val appDir = resolveAppDir()
        val testRoot = File(appDir, "src/test")
        val realMobileRegex = Regex("""(?<!\d)(?:\+98|0098|98|0)912[\s-]?\d{3}[\s-]?\d{4}(?!\d)""")
        val violations = mutableListOf<String>()
        testRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "json") }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    if (realMobileRegex.containsMatchIn(line)) {
                        violations += "${file.relativeTo(testRoot)}:${idx + 1}: $line"
                    }
                }
            }
        assertTrue("Personal-range 0912 numbers found in tests:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}

class LicenseNoticeTest {

    @Test
    fun `LICENSE preserves upstream attribution alongside PouyaRN and required security docs exist`() {
        val root = resolveRepoRoot()
        val license = File(root, "LICENSE")
        assertTrue("LICENSE must exist", license.exists())
        val text = license.readText()
        assertTrue("LICENSE must mention MIT License", text.contains("MIT License"))
        assertTrue("LICENSE must preserve upstream securityvisions copyright", text.contains("securityvisions"))
        assertTrue("LICENSE must include PouyaRN copyright", text.contains("PouyaRN"))

        val notice = File(root, "NOTICE.md")
        assertTrue("NOTICE.md must exist", notice.exists() && notice.length() > 50)

        val thirdParty = File(root, "THIRD_PARTY_NOTICES.md")
        assertTrue("THIRD_PARTY_NOTICES.md must exist", thirdParty.exists() && thirdParty.length() > 50)

        val security = File(root, "SECURITY.md")
        assertTrue("SECURITY.md must exist", security.exists() && security.length() > 50)

        val gitleaks = File(root, ".gitleaks.toml")
        assertTrue(".gitleaks.toml must exist", gitleaks.exists() && gitleaks.length() > 50)
    }
}

