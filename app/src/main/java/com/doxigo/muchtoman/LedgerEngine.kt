package com.doxigo.muchtoman

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Deep domain module encapsulating SQLite Room transactions, derivation pipelines,
 * categorization rules, and manual ledger overrides.
 *
 * Provides a clean serialized transaction boundary over [DurableDb] and [DerivedDb]
 * to guarantee transactional integrity and eliminate concurrency leaks.
 */
class LedgerEngine(
    private val context: Context,
    private val durable: DurableDb = DurableDb.get(context),
    private val derived: DerivedDb = DerivedDb.get(context),
) {
    private val mutex = Mutex()

    /**
     * Executes an action within the serialized ledger transaction boundary.
     */
    suspend fun <T> withLedgerLock(block: suspend () -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            block()
        }
    }

    /**
     * Re-derives the complete materialized ledger view from durable sources and active decisions.
     */
    suspend fun rederive(extraBankNumbers: Map<String, List<String>>): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val extra = extraLookup(extraBankNumbers)
            derive(durable, derived, extra)
        }
    }

    /**
     * Categorizes a single ledger entry with optional rule generation.
     */
    suspend fun categorise(
        entry: LedgerEntry,
        categoryId: String,
        always: Boolean,
        extraBankNumbers: Map<String, List<String>>,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val session = loadSession(durable)
            val previous = durable.decisions().forRef(entry.txn.ref)
                .firstOrNull { it.kind == DecisionKind.CATEGORY }
            val now = maxOf(System.currentTimeMillis(), (previous?.updatedAt ?: 0L) + 1L)

            if (entry.transfer) {
                derived.links().touching(entry.txn.ref)
                    .filter { it.kind == LinkKind.TRANSFER && it.auto }
                    .forEach { link ->
                        durable.linkDecisions().put(
                            linkDecision(
                                link.aRef,
                                link.bRef,
                                LinkKind.TRANSFER,
                                Verdict.REJECTED,
                                now,
                            )
                        )
                    }
            }

            durable.decisions().put(
                TxnDecision(
                    id = uuid7(now),
                    ref = entry.txn.ref,
                    kind = DecisionKind.CATEGORY,
                    value = categoryId,
                    createdAt = now,
                    updatedAt = now,
                    memberId = session?.member.orEmpty(),
                    familyRef = entry.txn.familyRef.ifBlank {
                        session?.let { familyTxnId(it.member, entry.txn.ref) }.orEmpty()
                    },
                )
            )

            if (always) {
                val addrKey = durable.smsSource().addrKeyOf(entry.txn.srcHash).orEmpty()
                durable.rules().put(ruleFrom(entry.txn, categoryId, addrKey, now))
            }

            derive(durable, derived, extraLookup(extraBankNumbers))
        }
    }

    /**
     * Pins or unpins a clean display alias for a merchant name.
     */
    suspend fun pinMerchantAlias(
        sourceNorm: String,
        displayName: String,
        extraBankNumbers: Map<String, List<String>>,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (displayName.isBlank()) {
                durable.merchantAliases().put(MerchantAlias(sourceNorm, "", now, now))
            } else {
                durable.merchantAliases().put(
                    MerchantAlias(
                        sourceNorm = sourceNorm,
                        displayName = displayName.trim(),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
            derive(durable, derived, extraLookup(extraBankNumbers))
        }
    }

    /**
     * Hides or reveals a transaction from the ledger without unnecessary re-derive.
     */
    suspend fun setTransactionHidden(
        entry: LedgerEntry,
        hidden: Boolean,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val session = loadSession(durable)
            val ref = entry.txn.ref
            val existing = durable.decisions().forRef(ref).firstOrNull { it.kind == DecisionKind.HIDE }
            durable.decisions().put(
                TxnDecision(
                    id = existing?.id ?: uuid7(now),
                    ref = ref,
                    kind = DecisionKind.HIDE,
                    value = "1",
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    deleted = !hidden,
                    memberId = session?.member.orEmpty(),
                    familyRef = entry.txn.familyRef.ifBlank { "" },
                )
            )
        }
    }

    /**
     * Overrides transaction flow direction ('in', 'out', or null to revert).
     */
    suspend fun setTransactionDirection(
        entry: LedgerEntry,
        direction: String?,
        extraBankNumbers: Map<String, List<String>>,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val session = loadSession(durable)
            val isRevert = direction == null
            durable.decisions().put(
                TxnDecision(
                    id = uuid7(now),
                    ref = entry.txn.ref,
                    kind = DecisionKind.DIRECTION,
                    value = direction,
                    createdAt = now,
                    updatedAt = now,
                    deleted = isRevert,
                    memberId = session?.member.orEmpty(),
                    familyRef = entry.txn.familyRef.ifBlank {
                        session?.let { familyTxnId(it.member, entry.txn.ref) }.orEmpty()
                    },
                )
            )
            derive(durable, derived, extraLookup(extraBankNumbers))
        }
    }
}
