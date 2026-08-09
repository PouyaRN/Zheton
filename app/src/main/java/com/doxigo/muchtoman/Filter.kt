package com.doxigo.muchtoman

/**
 * The timeline filter: a list of AND-composed tokens — field + operator + value.
 *
 * Pure and unit-testable — no DB, no ViewModel, no Compose. The sheet UI builds these, the
 * timeline consumes `matchesFilter` on its loaded rows, and the whole-ledger path (ticket 06)
 * runs the same predicate.
 */
enum class FilterField(val fa: String) {
    MERCHANT("فروشگاه"),
    CATEGORY("دسته"),
    BANK("بانک"),
    DIRECTION("جهت"),
    AMOUNT("مبلغ"),
    DATE("تاریخ"),
}

enum class FilterOp(val fa: String) {
    CONTAINS("شامل"),
    NOT_CONTAINS("بدون"),
    EQUALS("برابر"),
    NOT_EQUALS("نابرابر"),
    GTE("از"),
    LTE("تا"),
    REGEX("عبارت کتابخانه‌ای"),
}

data class FilterToken(
    val field: FilterField,
    val op: FilterOp,
    val value: String,
)

/**
 * Position-stable UI key for the LazyColumn row: field + op + index. Intentionally excludes
 * value so a keystroke never changes the key and the row stays alive under the user's cursor.
 * (Renamed from stableKey — index-based keys shift when earlier rows are removed.)
 */
fun FilterToken.positionKey(index: Int): String = "${field}_${op}_$index"

/** True when every token matches the row. An empty filter keeps everything. */
fun matchesFilter(entry: LedgerEntry, tokens: List<FilterToken>): Boolean =
    tokens.all { matchesToken(entry, it) }

fun matchesToken(entry: LedgerEntry, t: FilterToken): Boolean {
    val txn = entry.txn
    return when (t.field) {
        FilterField.MERCHANT ->
            stringToken(txn.merchant.ifBlank { txn.merchantNorm }, t)
        FilterField.CATEGORY -> if (t.op == FilterOp.REGEX) false else when (t.op) {
            FilterOp.EQUALS -> entry.categoryId == t.value
            FilterOp.NOT_EQUALS -> entry.categoryId != t.value
            else -> stringToken(entry.categoryFa, t)
        }
        FilterField.BANK -> if (t.op == FilterOp.REGEX) false else when (t.op) {
            FilterOp.EQUALS -> txn.bank == t.value
            FilterOp.NOT_EQUALS -> txn.bank != t.value
            else -> stringToken(bankNameOf(txn.bank), t)
        }
        FilterField.DIRECTION -> {
            val current = txn.direction ?: ""
            when (t.op) {
                FilterOp.EQUALS, FilterOp.CONTAINS -> current == t.value
                FilterOp.NOT_EQUALS, FilterOp.NOT_CONTAINS -> current != t.value
                // Direction is a keyword, not a string, so ranges and regex mean nothing.
                FilterOp.GTE, FilterOp.LTE, FilterOp.REGEX -> false
            }
        }
        FilterField.AMOUNT -> numberToken(txn.amountRial, t)
        FilterField.DATE -> numberToken(txn.day, t)
    }
}

private fun stringToken(hay: String, t: FilterToken): Boolean {
    val needle = t.value.trim()
    if (needle.isEmpty()) return true
    if (t.op == FilterOp.REGEX) {
        // Raw regex is merchant-scoped: a malformed expression is not a blank result, it is
        // treated as matching nothing so a typo never silently widens the filter.
        return runCatching { Regex(needle).containsMatchIn(hay) }.getOrDefault(false)
    }
    val hit = hay.trim().contains(needle, ignoreCase = true)
    return if (t.op == FilterOp.NOT_CONTAINS) !hit else hit
}

private fun numberToken(value: Long?, t: FilterToken): Boolean {
    val v = value ?: return false
    val parsed = t.value.trim().replace(",", "").toLongOrNull() ?: return false
    return when (t.op) {
        FilterOp.GTE -> v >= parsed
        FilterOp.LTE -> v <= parsed
        FilterOp.EQUALS -> v == parsed
        FilterOp.NOT_EQUALS -> v != parsed
        FilterOp.CONTAINS -> t.value.contains(v.toString())
        FilterOp.NOT_CONTAINS -> !t.value.contains(v.toString())
        FilterOp.REGEX -> false
    }
}