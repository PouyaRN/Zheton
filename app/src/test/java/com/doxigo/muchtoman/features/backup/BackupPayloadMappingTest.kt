package com.doxigo.muchtoman.features.backup

import com.doxigo.muchtoman.BalanceAnchor
import com.doxigo.muchtoman.Category
import com.doxigo.muchtoman.Commitment
import com.doxigo.muchtoman.CommitmentKind
import com.doxigo.muchtoman.FamilyMember
import com.doxigo.muchtoman.FamilyTxn
import com.doxigo.muchtoman.Goal
import com.doxigo.muchtoman.InstallmentObligation
import com.doxigo.muchtoman.InstallmentPayment
import com.doxigo.muchtoman.LinkDecision
import com.doxigo.muchtoman.ManualTxn
import com.doxigo.muchtoman.MerchantAlias
import com.doxigo.muchtoman.Reservation
import com.doxigo.muchtoman.Rule
import com.doxigo.muchtoman.SmsSource
import com.doxigo.muchtoman.SyncPublication
import com.doxigo.muchtoman.TxnDecision
import com.doxigo.muchtoman.widget.primitives.WidgetDensity
import com.doxigo.muchtoman.widget.engine.WidgetId
import com.doxigo.muchtoman.widget.engine.WidgetSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mirror mappers. Boring on purpose: every entity must survive a trip through its backup
 * shape byte for byte, because a field dropped here is a field a restore silently loses.
 */
class BackupPayloadMappingTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `every durable entity round trips through its mirror`() {
        val sms = SmsSource(
            srcHash = "a".repeat(64), sender = "9830001234", addrKey = "9830001234",
            body = "خرید ۱۰۰,۰۰۰ ریال", at = now, ingestedAt = now + 5, keyGen = 1,
        )
        assertEquals(sms, sms.toBackup().toEntity())

        val anchor = BalanceAnchor(
            id = "a1", accountId = "SAMAN", at = now, balanceRial = 12_000_000,
            source = "user", createdAt = now, updatedAt = now + 1, deleted = false,
        )
        assertEquals(anchor, anchor.toBackup().toEntity())

        val category = Category(
            id = "cat_food", parentId = null, nameFa = "خواربار", kind = "expense",
            sort = 10, builtin = true, archived = false, updatedAt = now,
        )
        assertEquals(category, category.toBackup().toEntity())

        val rule = Rule(
            id = "r1", priority = 900, categoryId = "cat_food", pMerchantNorm = "رفاه",
            pMerchantLike = null, pAddrKey = null, pBank = "SAMAN", pChannel = null,
            pDirection = "out", pMinRial = 1_000, pMaxRial = 5_000_000, pInstrument = null,
            enabled = true, builtin = false, originRef = "s:x:0",
            createdAt = now, updatedAt = now, deleted = false,
        )
        assertEquals(rule, rule.toBackup().toEntity())

        val decision = TxnDecision(
            id = "d1", ref = "s:${"b".repeat(64)}:0", kind = "category", value = "cat_food",
            createdAt = now, updatedAt = now + 2, deleted = false,
            memberId = "member", familyRef = "txn:member:x",
        )
        assertEquals(decision, decision.toBackup().toEntity())

        val link = LinkDecision(
            id = "l1", aRef = "a", bRef = "b", kind = "transfer", verdict = "same",
            createdAt = now, updatedAt = now, deleted = false,
        )
        assertEquals(link, link.toBackup().toEntity())

        val manual = ManualTxn(
            id = "m1", at = now, day = now / 86_400_000L, amountRial = -50_000,
            accountId = "MANUAL", categoryId = "cat_food", merchant = "نان",
            note = "صبح", createdAt = now, updatedAt = now, deleted = false,
        )
        assertEquals(manual, manual.toBackup().toEntity())

        val alias = MerchantAlias(sourceNorm = "رفاه", displayName = "سوپرمارکت", createdAt = now, updatedAt = now)
        assertEquals(alias, alias.toBackup().toEntity())

        val goal = Goal(
            id = "g1", nameFa = "سفر", targetRial = 50_000_000, kind = "save",
            categoryId = null, scopeMerchantNorm = "رفاه", period = "once",
            startsOn = now, endsOn = null, createdAt = now, updatedAt = now, deleted = false,
        )
        assertEquals(goal, goal.toBackup().toEntity())

        val member = FamilyMember(id = "f".repeat(32), name = "علی", sharesSms = true, updatedAt = now)
        assertEquals(member, member.toBackup().toEntity())

        val familyTxn = FamilyTxn(
            id = "txn:x:y", ownerMemberId = "f".repeat(32), sourceKind = "sms",
            at = now, day = now / 86_400_000L, amountRial = -1_000, bank = "SAMAN",
            merchant = "نان", updatedAt = now, deleted = false,
        )
        assertEquals(familyTxn, familyTxn.toBackup().toEntity())

        val publication = SyncPublication(
            id = "p1", sourceKind = "manual", contentHash = "hash", updatedAt = now, deleted = false,
        )
        assertEquals(publication, publication.toBackup().toEntity())

        val obligation = InstallmentObligation(
            id = "o1", provider = "دیجی‌پی", totalRial = 12_000_000, downPaymentRial = 2_000_000,
            installmentCount = 5, installmentRial = 2_000_000, startDate = now,
            notes = "یادداشت", linkedTxnRef = "s:x:0", createdAt = now, updatedAt = now, archived = false,
        )
        assertEquals(obligation, obligation.toBackup().toEntity())

        val payment = InstallmentPayment(
            id = "pay1", obligationId = "o1", installmentNumber = 2, dueDate = now,
            amountRial = 2_000_000, paidDate = now + 100, linkedTxnRef = "m:1",
        )
        assertEquals(payment, payment.toBackup().toEntity())

        val reservation = Reservation(
            id = "res1", purpose = "سفر", amountRial = 3_000_000, categoryId = "cat_travel",
            createdAt = now, updatedAt = now, released = true,
        )
        assertEquals(reservation, reservation.toBackup().toEntity())
    }

    @Test
    fun `commitment enums are preserved and unknown values fall back`() {
        val commitment = Commitment(
            id = "c1", title = "وام", provider = "ملت", kind = CommitmentKind.LOAN,
            totalAmountRial = 10_000_000, remainingAmountRial = 5_000_000,
            installmentCount = 6, paidInstallmentCount = 3, nextDueTimestamp = now,
            note = "", isSettled = false, createdAt = now,
        )
        assertEquals(commitment, commitment.toBackup().toEntity())
        assertEquals("LOAN", commitment.toBackup().kind)

        val future = BackupCommitment(
            id = "c2", title = "وام", provider = "ملت", kind = "SOME_FUTURE_KIND",
            totalAmountRial = 1, remainingAmountRial = 1,
        )
        assertEquals(CommitmentKind.BNPL, future.toEntity().kind)
    }

    @Test
    fun `widget slots survive known ids and drop unknown ones`() {
        val slot = WidgetSlot(
            id = WidgetId.NET_WORTH,
            density = WidgetDensity.EXPANDED,
            isPinned = true,
            isVisible = true,
            order = 0,
        )
        assertEquals(slot, slot.toBackup().toEntityOrNull())

        assertNull(BackupWidgetSlot(id = "A_WIDGET_FROM_2030", density = "STANDARD").toEntityOrNull())
        assertNull(BackupWidgetSlot(id = "NET_WORTH", density = "A_FUTURE_DENSITY").toEntityOrNull())
    }
}
