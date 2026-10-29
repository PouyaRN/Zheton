package com.doxigo.muchtoman

import kotlin.math.abs
import kotlin.math.min

/**
 * Which category a new hand-typed expense probably belongs to, read off her own ledger.
 *
 * Not a model and not a rule: nothing is written, nothing is learned, and nothing here ever
 * files a transaction. It only decides which button the quick-add row selects first and what
 * the one line above it says, and she can tap another. The evidence is the rows she already
 * filed — a shipped guess still waiting for her answer ([LedgerEntry.needsReview]) is the app's
 * opinion, not hers, and does not count.
 *
 * Ranking, per category, compared in this order:
 *  1. how many of her rows at the **same place** (merchant, or the alias she renamed it to)
 *     went to it — a place is the strongest thing she tells us;
 *  2. how many sit in the **same time-of-day window** (±[HOUR_WINDOW_MS], Tehran clock) plus
 *     how many sit in the **same amount band** (half to double);
 *  3. rows from the same account;
 *  4. how often she uses it at all;
 *  5. most recently used, then id — so two phones with the same ledger agree.
 *
 * No history in the requested direction means no suggestion and no reason line: the screen
 * shows its default row and says nothing, rather than guessing out loud.
 */
data class CategorySuggestion(
    /** The category to pre-select, or null when there is no history to go on. */
    val top: Category?,
    /** Up to three more for the row, best first. Never contains [top]. */
    val next: List<Category>,
    /** One conversational line naming the evidence, or null exactly when [top] is null. */
    val reason: String?,
    /** What the reason line rests on; empty when [top] is null. */
    val evidence: Set<SuggestEvidence> = emptySet(),
) {
    companion object {
        val NONE = CategorySuggestion(top = null, next = emptyList(), reason = null)
    }
}

enum class SuggestEvidence { SAME_PLACE, SAME_NOTE, THIS_HOUR, THIS_AMOUNT, FREQUENCY }

/** Half-width of the time-of-day window: 10:42 matches anything between 09:12 and 12:12. */
const val HOUR_WINDOW_MS = 90 * 60 * 1000L

/** Hour and amount only count as evidence once they have happened more than once. */
private const val USUALLY = 2

/**
 * Rank categories for a new transaction from [entries] (normally `LedgerView.allEntries`).
 *
 * @param amountRial magnitude in Rial; null or 0 skips the amount band.
 * @param accountId the account picked on the screen, or null.
 * @param at when the transaction happened (epoch millis); only its Tehran time of day is used.
 * @param merchant what she typed as the place, matched against both the source name and the
 *   alias she gave it.
 * @param note her note, used as the place when no merchant was typed.
 * @param categories the live category list; history pointing at an archived, hidden or
 *   unknown category is skipped, and [CategorySuggestion.next] is padded from it in its own
 *   order when history offers fewer than three.
 * @param notes ref → note text (`LedgerView.notes`) so a note can match earlier notes.
 * @param direction "out" for an expense, "in" for income.
 */
fun suggestCategory(
    entries: List<LedgerEntry>,
    categories: List<Category>,
    amountRial: Long?,
    accountId: String?,
    at: Long,
    merchant: String? = null,
    note: String? = null,
    notes: Map<String, String> = emptyMap(),
    direction: String = "out",
    hidden: Set<String> = emptySet(),
): CategorySuggestion {
    val offered = categoryChoices(categories, direction, hidden)
    val byId = offered.associateBy { it.id }

    val history = entries.filter {
        !it.duplicate && !it.transfer && !it.hidden && !it.needsReview &&
            it.txn.direction == direction && it.categoryId in byId
    }
    if (history.isEmpty()) return CategorySuggestion.NONE

    val place = merchant?.let(::merchantNorm).orEmpty()
    val noteKey = note?.let(::merchantNorm).orEmpty()
    val amount = amountRial?.let(::abs)?.takeIf { it > 0 }
    val minute = timeOfDay(at)

    val tallies = HashMap<String, Tally>()
    for (e in history) {
        val t = tallies.getOrPut(e.categoryId) { Tally(e.categoryId) }
        t.total++
        if (e.txn.at > t.lastAt) t.lastAt = e.txn.at
        if (place.isNotEmpty() &&
            (e.txn.merchantNorm == place || merchantNorm(e.txn.merchant) == place)
        ) t.place++
        if (place.isEmpty() && noteKey.isNotEmpty() &&
            (notes[e.txn.ref]?.let(::merchantNorm) == noteKey || e.txn.merchantNorm == noteKey)
        ) t.note++
        if (circularDistance(timeOfDay(e.txn.at), minute) <= HOUR_WINDOW_MS) t.hour++
        val a = e.txn.amountRial
        if (amount != null && a != null && a > 0 && a * 2 >= amount && a <= amount * 2) t.amount++
        if (accountId != null && e.txn.accountId == accountId) t.account++
    }

    val ranked = tallies.values.sortedWith(
        compareByDescending<Tally> { it.place }
            .thenByDescending { it.note }
            .thenByDescending { it.hour + it.amount }
            .thenByDescending { it.account }
            .thenByDescending { it.total }
            .thenByDescending { it.lastAt }
            .thenBy { it.categoryId }
    )

    val best = ranked.first()
    val top = byId.getValue(best.categoryId)
    val fromHistory = ranked.drop(1).map { byId.getValue(it.categoryId) }
    val padding = offered.filter { c -> c.id != top.id && fromHistory.none { it.id == c.id } }
    val next = (fromHistory + padding).take(3)

    val evidence = buildSet {
        if (best.place > 0) add(SuggestEvidence.SAME_PLACE)
        if (best.note > 0) add(SuggestEvidence.SAME_NOTE)
        if (best.hour >= USUALLY) add(SuggestEvidence.THIS_HOUR)
        if (best.amount >= USUALLY) add(SuggestEvidence.THIS_AMOUNT)
        if (isEmpty()) add(SuggestEvidence.FREQUENCY)
    }
    return CategorySuggestion(top, next, suggestionReason(top.nameFa, evidence), evidence)
}

/**
 * The line above the row, in the canvas's voice: «به‌نظر {دسته}؛ …» and then the strongest
 * evidence there is. Place beats note beats hour beats amount; at most two are named.
 */
fun suggestionReason(categoryFa: String, evidence: Set<SuggestEvidence>): String {
    val place = SuggestEvidence.SAME_PLACE in evidence
    val noted = SuggestEvidence.SAME_NOTE in evidence
    val hour = SuggestEvidence.THIS_HOUR in evidence
    val amount = SuggestEvidence.THIS_AMOUNT in evidence
    val why = when {
        place && hour -> "معمولاً این ساعت همین‌جا خرج می‌کنی"
        place && amount -> "معمولاً همین‌جا همین‌قدر خرج می‌کنی"
        place -> "قبلاً هم همین‌جا رو همین زدی"
        noted -> "قبلاً هم با همین یادداشت همین رو زدی"
        hour && amount -> "معمولاً این ساعت همین‌قدر خرج می‌کنی"
        hour -> "معمولاً این ساعت خرجت همینه"
        amount -> "خرج‌های همین‌قدری‌ات معمولاً همینه"
        else -> "بیشتر خرج‌هات همینه"
    }
    return "به‌نظر $categoryFa؛ $why"
}

private class Tally(val categoryId: String) {
    var place = 0
    var note = 0
    var hour = 0
    var amount = 0
    var account = 0
    var total = 0
    var lastAt = Long.MIN_VALUE
}

/** Milliseconds since Tehran midnight. */
private fun timeOfDay(at: Long): Long = Math.floorMod(at + TEHRAN_OFFSET_MS, DAY_MS)

/** Distance on the 24-hour clock, so 23:30 and 00:30 are an hour apart. */
private fun circularDistance(a: Long, b: Long): Long {
    val d = abs(a - b)
    return min(d, DAY_MS - d)
}
