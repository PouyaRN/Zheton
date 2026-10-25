package com.doxigo.muchtoman.features.reminders

import android.content.Context
import com.doxigo.muchtoman.Commitment
import com.doxigo.muchtoman.CommitmentKind
import com.doxigo.muchtoman.DAY_MS
import com.doxigo.muchtoman.DurableDb
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.tehranDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reading the things that have a due date and turning them into [ReminderItem]s.
 *
 * Two sources today, and they are the two the app already treats as obligations:
 *
 *  * `Commitment` — BNPL, loans, cheques and financed purchases she entered by hand. These carry
 *    a single `nextDueTimestamp` that the app updates as installments are paid.
 *  * `InstallmentPayment` — the per-payment schedule of a tracked purchase. Reading only the
 *    obligation would remind her about the purchase, not the payment that is actually due this
 *    month, and those are different days.
 *
 * The prefix in [ReminderItem.id] is what keeps the dedupe keys stable across app upgrades and
 * makes them unique when the two sources happen to share an id shape: a commitment id and an
 * installment payment id are both opaque strings.
 */
suspend fun loadReminderItems(
    context: Context,
    nowMillis: Long = System.currentTimeMillis(),
): List<ReminderItem> = withContext(Dispatchers.IO) {
    val store = Store(context)
    val fromCommitments = store.commitments
        .asSequence()
        .filter { !it.isSettled && it.nextDueTimestamp > 0L }
        .map { it.toReminderItem() }
        .toList()

    // The installment side is Room, and the query deliberately looks a little into the future:
    // the planner is the one that decides the lead window, and it needs the rows to decide on.
    val futureWindow = nowMillis + INSTALLMENT_LOOKAHEAD_DAYS * DAY_MS
    val durable = DurableDb.get(context)
    val obligations = durable.obligations().active().associateBy { it.id }
    val fromInstallments = durable.installmentPayments()
        .unpaidDueBefore(futureWindow)
        .mapNotNull { payment ->
            val obligation = obligations[payment.obligationId] ?: return@mapNotNull null
            ReminderItem(
                id = "installment:${payment.id}",
                kind = ReminderKind.INSTALLMENT,
                dueDay = tehranDay(payment.dueDate),
                // "قسط ۲" is what she calls it; the provider alone could name three
                // different payments in the same month.
                label = obligation.provider.ifBlank { "قسط" } +
                    " — قسط " + faNumber(payment.installmentNumber),
            )
        }

    (fromCommitments + fromInstallments).distinctBy { it.id to it.dueDay }
}

/** One commitment as one reminder. */
fun Commitment.toReminderItem(): ReminderItem = ReminderItem(
    id = "commitment:$id",
    kind = kind.toReminderKind(),
    dueDay = tehranDay(nextDueTimestamp),
    label = title.ifBlank { provider.ifBlank { kind.toReminderKind().fa } },
)

/** The domain enum is about products; the reminder enum is about what the sentence should say. */
fun CommitmentKind.toReminderKind(): ReminderKind = when (this) {
    CommitmentKind.CHECK -> ReminderKind.CHECK
    CommitmentKind.LOAN -> ReminderKind.LOAN
    CommitmentKind.BNPL -> ReminderKind.BNPL
    CommitmentKind.INSTALLMENT -> ReminderKind.INSTALLMENT
}

/**
 * Four months of schedule is far more than the planner's 45-day horizon, with room for the
 * overdue grace window behind it. Reading more would only make the worker do work nobody sees.
 */
private const val INSTALLMENT_LOOKAHEAD_DAYS = 120L
