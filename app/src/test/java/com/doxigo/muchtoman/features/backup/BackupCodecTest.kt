package com.doxigo.muchtoman.features.backup

import com.doxigo.muchtoman.BankAccount
import com.doxigo.muchtoman.Holding
import com.doxigo.muchtoman.StrangeSender
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val now = 1_700_000_000_000L
    private val day = now / 86_400_000L
    private val codec = BackupCodec()

    // ──────────────────────────── a payload that uses every section ────────────────────────────

    private fun sample(): BackupPayload = BackupPayload(
        schemaVersion = BACKUP_SCHEMA_VERSION,
        createdAt = now,
        appVersion = "1.0.5",
        includesMessages = true,
        prefs = BackupPrefs(
            name = "مریم",
            themeMode = "DARK",
            smsEnabled = true,
            widgetLock = true,
            showHidden = false,
            hourlyWageToman = 850_000,
            cycleMode = "ANCHOR",
            cycleAnchorDay = 5,
            smsScannedTo = now - 1_000,
            seenSms = setOf("a".repeat(64)),
        ),
        ledger = BackupLedger(
            holdings = listOf(Holding(typeId = "usd", amount = 1_200.0, label = "دلار", id = "h1")),
            commitments = listOf(
                BackupCommitment(
                    id = "c1", title = "وام", provider = "ملت", kind = "LOAN",
                    totalAmountRial = 10_000_000, remainingAmountRial = 5_000_000,
                    installmentCount = 6, paidInstallmentCount = 3,
                    nextDueTimestamp = now, createdAt = now,
                )
            ),
            overrides = mapOf("usd" to 1_000_000.0),
            history = mapOf(day - 3 to 1_234.0),
            bankAccounts = listOf(
                BankAccount(bank = "SAMAN", mask = "1234", balance = 2_000_000.0, updatedAt = now, anchored = true)
            ),
            disabledBanks = setOf("SAMAN"),
            hiddenCategories = setOf("cat_x"),
            extraBankNumbers = mapOf("SAMAN" to listOf("9830001234")),
            dismissedSenders = setOf("sender"),
            strangeSenders = listOf(StrangeSender(sender = "12345", bank = "SAMAN", snippet = "خرید", at = now)),
            widgetSlots = listOf(BackupWidgetSlot(id = "NET_WORTH", density = "EXPANDED", order = 0)),
        ),
        tables = BackupTables(
            messages = listOf(
                BackupSmsSource(
                    srcHash = "a".repeat(64), sender = "9830001234", addrKey = "9830001234",
                    body = "خرید ۱۰۰,۰۰۰ ریال", at = now, ingestedAt = now, keyGen = 1,
                )
            ),
            meta = listOf(BackupMeta("source_scanned_to", now.toString())),
            anchors = listOf(
                BackupBalanceAnchor(
                    id = "a1", accountId = "SAMAN", at = now, balanceRial = 10_000_000,
                    source = "user", createdAt = now, updatedAt = now,
                )
            ),
            categories = listOf(
                BackupCategory(id = "cat_food", nameFa = "خواربار", kind = "expense", sort = 10, builtin = true, updatedAt = now)
            ),
            rules = listOf(
                BackupRule(id = "r1", priority = 900, categoryId = "cat_food", pMerchantNorm = "رفاه", enabled = true, createdAt = now, updatedAt = now)
            ),
            decisions = listOf(
                BackupTxnDecision(id = "d1", ref = "s:${"b".repeat(64)}:0", kind = "category", value = "cat_food", createdAt = now, updatedAt = now)
            ),
            linkDecisions = listOf(
                BackupLinkDecision(id = "l1", aRef = "a", bRef = "b", kind = "transfer", verdict = "same", createdAt = now, updatedAt = now)
            ),
            manualTxns = listOf(
                BackupManualTxn(id = "m1", at = now, day = day, amountRial = -50_000, merchant = "نان", createdAt = now, updatedAt = now)
            ),
            merchantAliases = listOf(
                BackupMerchantAlias(sourceNorm = "رفاه", displayName = "سوپرمارکت", createdAt = now, updatedAt = now)
            ),
            goals = listOf(
                BackupGoal(id = "g1", nameFa = "سفر", targetRial = 50_000_000, kind = "save", period = "once", startsOn = now, createdAt = now, updatedAt = now)
            ),
            familyMembers = listOf(BackupFamilyMember(id = "f".repeat(32), name = "علی", updatedAt = now)),
            familyTxns = listOf(
                BackupFamilyTxn(id = "txn:x:y", ownerMemberId = "f".repeat(32), sourceKind = "manual", at = now, day = day, amountRial = -1_000, updatedAt = now)
            ),
            syncPublications = listOf(
                BackupSyncPublication(id = "p1", sourceKind = "manual", contentHash = "h", updatedAt = now)
            ),
            obligations = listOf(
                BackupInstallmentObligation(
                    id = "o1", provider = "دیجی‌پی", totalRial = 12_000_000, downPaymentRial = 2_000_000,
                    installmentCount = 5, installmentRial = 2_000_000, startDate = now, createdAt = now, updatedAt = now,
                )
            ),
            payments = listOf(
                BackupInstallmentPayment(id = "pay1", obligationId = "o1", installmentNumber = 1, dueDate = now, amountRial = 2_000_000)
            ),
            reservations = listOf(
                BackupReservation(id = "res1", purpose = "سفر", amountRial = 3_000_000, createdAt = now, updatedAt = now)
            ),
        ),
    )

    // ──────────────────────────── round trip ────────────────────────────

    @Test
    fun `a full payload survives encode and decode unchanged`() {
        val payload = sample()
        val decoded = codec.decode(codec.encode(payload), now + 1_000)
        assertEquals(payload, decoded)
        assertTrue(decoded.tables.messages.isNotEmpty())
        assertEquals(1, decoded.ledger.holdings.size)
        assertEquals("مریم", decoded.prefs.name)
    }

    @Test
    fun `an unknown extra field written by a later build is ignored`() {
        val text = String(codec.encode(sample()), Charsets.UTF_8)
        val withExtra = text.replaceFirst("{", """{"someLaterField":{"a":1},""")
        assertNotNull(codec.decode(withExtra.toByteArray(), now + 1_000))
    }

    @Test
    fun `the payload size cap is enforced before parsing`() {
        val tiny = BackupCodec(maxPayloadBytes = 8)
        assertThrows(BackupFormatException::class.java) {
            tiny.decode(ByteArray(64) { 'a'.code.toByte() })
        }
    }

    @Test
    fun `encode refuses to write a version that is not current`() {
        assertThrows(BackupFormatException::class.java) {
            codec.encode(sample().copy(schemaVersion = 0))
        }
    }

    // ──────────────────────────── version gate and migrations ────────────────────────────

    @Test
    fun `a future schema version is refused rather than guessed at`() {
        val text = String(codec.encode(sample()), Charsets.UTF_8)
            .replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")
        val error = assertThrows(UnsupportedBackupVersionException::class.java) {
            codec.decode(text.toByteArray(), now + 1_000)
        }
        assertEquals(2, error.version)
    }

    @Test
    fun `a missing or negative schema version is refused`() {
        assertThrows(BackupFormatException::class.java) {
            codec.decode("""{"createdAt":0}""".toByteArray())
        }
        val negative = String(codec.encode(sample()), Charsets.UTF_8)
            .replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":-1")
        assertThrows(BackupFormatException::class.java) {
            codec.decode(negative.toByteArray())
        }
    }

    @Test
    fun `an older schema climbs the registered migration chain`() {
        var migrated = false
        val migrating = BackupCodec(
            migrations = mapOf(
                0 to { obj ->
                    migrated = true
                    JsonObject(obj.toMutableMap().apply { put("schemaVersion", JsonPrimitive(1)) })
                }
            )
        )
        val payload = migrating.decode("""{"schemaVersion":0,"createdAt":0}""".toByteArray())
        assertTrue(migrated)
        assertEquals(BACKUP_SCHEMA_VERSION, payload.schemaVersion)
    }

    @Test
    fun `an older schema without a migration is refused`() {
        assertThrows(BackupFormatException::class.java) {
            BackupCodec().decode("""{"schemaVersion":0,"createdAt":0}""".toByteArray())
        }
    }

    // ──────────────────────────── validation is a security boundary ────────────────────────────

    @Test
    fun `sync credentials in durable meta never survive decode`() {
        val payload = sample().let {
            it.copy(
                tables = it.tables.copy(
                    meta = it.tables.meta + BackupMeta("sync_key", "household-key") +
                        BackupMeta("sync_token", "hid.secret")
                )
            )
        }
        val decoded = codec.decode(codec.encode(payload), now + 1_000)
        assertEquals(1, decoded.tables.meta.size)
        assertEquals("source_scanned_to", decoded.tables.meta.first().k)
    }

    @Test
    fun `a backup that claims to omit messages but carries them is refused`() {
        assertThrows(BackupFormatException::class.java) {
            codec.decode(codec.encode(sample().copy(includesMessages = false)), now + 1_000)
        }
    }

    @Test
    fun `an invalid message hash is refused`() {
        val payload = sample()
        val broken = payload.copy(
            tables = payload.tables.copy(
                messages = listOf(payload.tables.messages.first().copy(srcHash = "not-a-hash"))
            )
        )
        assertThrows(BackupFormatException::class.java) {
            codec.decode(codec.encode(broken), now + 1_000)
        }
    }

    @Test
    fun `timestamps far in the future are refused`() {
        assertThrows(BackupFormatException::class.java) {
            codec.decode(codec.encode(sample().copy(createdAt = now + 40L * 86_400_000L)), now)
        }
    }

    @Test
    fun `an impossible rate is refused`() {
        val payload = sample()
        val broken = payload.copy(ledger = payload.ledger.copy(overrides = mapOf("usd" to 1e30)))
        assertThrows(BackupFormatException::class.java) {
            codec.decode(codec.encode(broken), now + 1_000)
        }
    }

    @Test
    fun `an unknown category kind is refused`() {
        val payload = sample()
        val broken = payload.copy(
            tables = payload.tables.copy(
                categories = listOf(payload.tables.categories.first().copy(kind = "savings-account"))
            )
        )
        assertThrows(BackupFormatException::class.java) {
            codec.decode(codec.encode(broken), now + 1_000)
        }
    }

    @Test
    fun `a long free-text note still round trips`() {
        val note = "ی".repeat(5_000)
        val payload = sample()
        val withNote = payload.copy(
            tables = payload.tables.copy(
                manualTxns = listOf(
                    BackupManualTxn(
                        id = "m-long", at = now, day = day, amountRial = -1_000,
                        merchant = "فروشگاه", note = note, createdAt = now, updatedAt = now,
                    )
                )
            )
        )
        val decoded = codec.decode(codec.encode(withNote), now + 1_000)
        assertEquals(note, decoded.tables.manualTxns.single().note)
    }

    @Test
    fun `a negative cycle anchor day is refused`() {
        val payload = sample().let { it.copy(prefs = it.prefs.copy(cycleAnchorDay = 40)) }
        assertThrows(BackupFormatException::class.java) {
            codec.decode(codec.encode(payload), now + 1_000)
        }
    }

    // ──────────────────────────── summary ────────────────────────────

    @Test
    fun `summary counts what the confirmation sheet shows`() {
        val summary = sample().summary(bytes = 4_096)
        assertEquals(1, summary.messages)
        assertEquals(1, summary.manualTransactions)
        assertEquals(2, summary.decisions) // one decision plus one link decision
        assertEquals(1, summary.rules)
        assertEquals(1, summary.categories)
        assertEquals(1, summary.holdings)
        assertEquals(1, summary.bankAccounts)
        assertEquals(1, summary.commitments)
        assertEquals(1, summary.goals)
        assertEquals(1, summary.obligations)
        assertEquals(1, summary.reservations)
        assertEquals(4_096L, summary.bytes)
        assertTrue(summary.totalRows >= 11)
    }
}
