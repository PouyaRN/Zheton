package com.doxigo.muchtoman

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Goals, and the one place this app is willing to encourage anybody.
 *
 * Concrete, monitored goals are the encouragement with actual evidence behind them, which is
 * why they get a table and streaks do not. Progress is **computed, never stored**: a stored
 * figure is one that can drift away from the transactions underneath it, and then the app is
 * congratulating her on a number it made up.
 *
 * Two shapes, and no more:
 *  - `save` — put this much aside by then.
 *  - `cap`  — keep this category under this much, this month.
 */

object GoalKind {
    const val SAVE = "save"
    const val CAP = "cap"
}

object GoalPeriod {
    const val MONTH = "jmonth"
    const val ONCE = "once"
}

@Entity(tableName = "goal")
data class Goal(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name_fa") val nameFa: String,
    @ColumnInfo(name = "target_rial") val targetRial: Long,
    val kind: String,
    /** For a cap, which category it holds down. Null on a savings goal. */
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    /**
     * For a cap scoped to a merchant: the stable merchant identity (merchantNorm).
     * When set, the cap counts spending at this merchant. Null when scoped to a category.
     */
    @ColumnInfo(name = "scope_merchant_norm", defaultValue = "NULL") val scopeMerchantNorm: String? = null,
    val period: String,
    @ColumnInfo(name = "starts_on") val startsOn: Long,
    @ColumnInfo(name = "ends_on") val endsOn: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: Goal)

    @Query("SELECT * FROM goal WHERE deleted = 0 ORDER BY created_at")
    suspend fun active(): List<Goal>

    @Query("UPDATE goal SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun delete(id: String, now: Long)
}

/** Where a goal stands, worked out from the transactions and nothing else. */
data class GoalProgress(
    val goal: Goal,
    val currentRial: Long,
    val targetRial: Long,
    /** 0..1, clamped. A cap counts down: 1.0 means the cap is used up, not met. */
    val share: Float,
    /**
     * True when a savings goal is under water — she has spent more than came in since it
     * started, so nothing has been put aside.
     *
     * Kept as a flag rather than shown as a negative figure. "−۴۵ million of 50 million" is
     * arithmetic read aloud and lands as a scolding; nothing was set aside, and that is what
     * the screen says.
     */
    val underWater: Boolean,
    val done: Boolean,
    /** Null for a goal with no end. */
    val daysLeft: Int?,
    /**
     * Pace estimate for caps: the Jalali day-of-month by which the cap will be exceeded at
     * the current daily rate. Null when the cap is not on track to be exceeded this month,
     * or when there is no spending yet.
     */
    val paceDay: Int? = null,
) {
    val cap: Boolean get() = goal.kind == GoalKind.CAP
}

/**
 * A savings goal counts what was kept — income minus spending since it started, transfers left
 * out of both. A cap counts what has been spent in its category (and all children) this Jalali
 * month, reading the whole stored ledger.
 */
fun goalProgress(goal: Goal, entries: List<LedgerEntry>, today: Long, categories: List<Category> = emptyList()): GoalProgress {
    val rows = spendable(entries)
    val current = when (goal.kind) {
        GoalKind.CAP -> {
            val from = jalaliMonthStart(today)
            val matching = when {
                // Merchant scope: count all spending at this merchant identity.
                goal.scopeMerchantNorm != null ->
                    rows.filter { it.txn.day >= from && it.txn.merchantNorm == goal.scopeMerchantNorm }
                // Category scope: count the category AND all descendants recursively.
                goal.categoryId != null -> {
                    fun descendants(parentId: String): List<String> {
                        val kids = categories.filter { it.parentId == parentId }.map { it.id }
                        return kids + kids.flatMap { descendants(it) }
                    }
                    val catIds = setOf(goal.categoryId) + descendants(goal.categoryId)
                    rows.filter { it.txn.day >= from && it.categoryId in catIds }
                }
                // No scope: count nothing (graceful degradation).
                else -> emptyList()
            }
            matching.sumOf { -(it.txn.signedRial ?: 0L).coerceAtMost(0L) }
        }
        else -> rows.filter { it.txn.day >= goal.startsOn }.sumOf { it.txn.signedRial ?: 0L }
    }
    val share = if (goal.targetRial > 0) {
        (current.toDouble() / goal.targetRial).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    // Pace estimate: at the current daily rate, which Jalali day will the cap be exceeded?
    // Only shown once usage passes ~80% of the cap.
    val paceDay = if (goal.kind == GoalKind.CAP && goal.targetRial > 0 && share >= 0.8f && current > 0) {
        val from = jalaliMonthStart(today)
        val dayOfMonth = jalaliOf(today).day
        val dailyRate = current.toDouble() / dayOfMonth.coerceAtLeast(1)
        if (dailyRate > 0) {
            val daysToCap = ((goal.targetRial - current).toDouble() / dailyRate).toInt()
            val estimatedDay = dayOfMonth + daysToCap
            if (estimatedDay in 1..31) estimatedDay else null
        } else null
    } else null

    return GoalProgress(
        goal = goal,
        currentRial = if (goal.kind == GoalKind.CAP) current else current.coerceAtLeast(0L),
        targetRial = goal.targetRial,
        underWater = goal.kind != GoalKind.CAP && current < 0,
        share = share,
        done = if (goal.kind == GoalKind.CAP) current <= goal.targetRial else current >= goal.targetRial,
        daysLeft = goal.endsOn?.let { (it - today).toInt() },
        paceDay = paceDay,
    )
}

