package com.doxigo.muchtoman

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A credit/incremental purchase tracked manually by the user.
 *
 * Bank-independent: an obligation belongs to the provider/purchase, not to a specific bank
 * account. Future installment payments can come from any card. The optional [linkedTxnRef]
 * ties it to the original down-payment transaction imported from SMS.
 */
@Entity(
    tableName = "installment_obligation",
    indices = [Index("archived"), Index("created_at")],
)
data class InstallmentObligation(
    @PrimaryKey val id: String,
    /** The credit provider or store name (e.g. "دیجی پی", "مسکن"). */
    val provider: String,
    /** Total purchase price in whole Rial. */
    @ColumnInfo(name = "total_rial") val totalRial: Long,
    /** Amount already deducted (down payment) in whole Rial. */
    @ColumnInfo(name = "down_payment_rial") val downPaymentRial: Long,
    /** Number of installments. */
    @ColumnInfo(name = "installment_count") val installmentCount: Int,
    /** Amount per installment in whole Rial. */
    @ColumnInfo(name = "installment_rial") val installmentRial: Long,
    /** When the first installment is due (epoch millis). */
    @ColumnInfo(name = "start_date") val startDate: Long,
    /** Free-text notes. */
    val notes: String = "",
    /** Ref of the SMS transaction that represents the down payment, if any. */
    @ColumnInfo(name = "linked_txn_ref") val linkedTxnRef: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val archived: Boolean = false,
) {
    /** Remaining to pay across all unpaid installments. */
    fun remainingRial(paidCount: Int): Long =
        (totalRial - downPaymentRial) - (paidCount.toLong() * installmentRial)
}

/**
 * One installment in an obligation's payment schedule. Each row is one monthly payment.
 * When [paidDate] is non-null, the installment has been paid. [linkedTxnRef] optionally
 * ties it to the bank transaction that covered this payment.
 */
@Entity(
    tableName = "installment_payment",
    indices = [
        Index("obligation_id"),
        Index("obligation_id", "paid_date"),
    ],
)
data class InstallmentPayment(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "obligation_id") val obligationId: String,
    /** 1-based installment number. */
    @ColumnInfo(name = "installment_number") val installmentNumber: Int,
    /** When this installment is due (epoch millis). */
    @ColumnInfo(name = "due_date") val dueDate: Long,
    /** Amount of this installment in whole Rial. */
    @ColumnInfo(name = "amount_rial") val amountRial: Long,
    /** When it was actually paid (null = unpaid). */
    @ColumnInfo(name = "paid_date") val paidDate: Long? = null,
    /** Ref of the bank transaction that paid this installment, if linked. */
    @ColumnInfo(name = "linked_txn_ref") val linkedTxnRef: String = "",
)

// ──────────────────────────── DAOs ────────────────────────────

@Dao
interface InstallmentObligationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(obligation: InstallmentObligation)

    @Query("SELECT * FROM installment_obligation WHERE archived = 0 ORDER BY created_at DESC")
    suspend fun active(): List<InstallmentObligation>

    @Query("SELECT * FROM installment_obligation ORDER BY created_at DESC")
    suspend fun all(): List<InstallmentObligation>

    @Query("SELECT * FROM installment_obligation WHERE id = :id")
    suspend fun get(id: String): InstallmentObligation?

    @Query("UPDATE installment_obligation SET archived = 1, updated_at = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long)

    @Query("UPDATE installment_obligation SET archived = 0, updated_at = :now WHERE id = :id")
    suspend fun unarchive(id: String, now: Long)
}

@Dao
interface InstallmentPaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(payment: InstallmentPayment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(payments: List<InstallmentPayment>)

    @Query("SELECT * FROM installment_payment WHERE obligation_id = :obligationId ORDER BY installment_number")
    suspend fun forObligation(obligationId: String): List<InstallmentPayment>

    @Query("SELECT * FROM installment_payment WHERE obligation_id = :obligationId AND paid_date IS NULL ORDER BY due_date")
    suspend fun unpaid(obligationId: String): List<InstallmentPayment>

    @Query("SELECT * FROM installment_payment WHERE paid_date IS NULL AND due_date <= :before ORDER BY due_date")
    suspend fun unpaidDueBefore(before: Long): List<InstallmentPayment>

    @Query("SELECT COUNT(*) FROM installment_payment WHERE obligation_id = :obligationId AND paid_date IS NOT NULL")
    suspend fun paidCount(obligationId: String): Int

    @Query("UPDATE installment_payment SET paid_date = :paidDate, linked_txn_ref = :txnRef WHERE id = :id")
    suspend fun markPaid(id: String, paidDate: Long, txnRef: String)

    @Query("UPDATE installment_payment SET paid_date = :paidDate WHERE obligation_id = :obligationId AND paid_date IS NULL")
    suspend fun markAllPaid(obligationId: String, paidDate: Long)

    @Query("SELECT * FROM installment_payment WHERE linked_txn_ref = :txnRef")
    suspend fun byTxnRef(txnRef: String): List<InstallmentPayment>

    @Query(
        "SELECT * FROM installment_payment WHERE paid_date IS NULL " +
            "AND amount_rial = :amount AND due_date BETWEEN :from AND :to " +
            "ORDER BY ABS(due_date - :reference) ASC"
    )
    suspend fun findMatchingUnpaid(amount: Long, from: Long, to: Long, reference: Long): List<InstallmentPayment>
}

// ──────────────────────────── helpers ────────────────────────────

/**
 * Create an obligation and its payment schedule from the user's input.
 * Returns the obligation and the list of payments to insert.
 */
fun buildObligation(
    id: String,
    provider: String,
    totalRial: Long,
    downPaymentRial: Long,
    installmentCount: Int,
    installmentRial: Long,
    startDate: Long,
    notes: String,
    linkedTxnRef: String,
    now: Long,
): Pair<InstallmentObligation, List<InstallmentPayment>> {
    val obligation = InstallmentObligation(
        id = id,
        provider = provider,
        totalRial = totalRial,
        downPaymentRial = downPaymentRial,
        installmentCount = installmentCount,
        installmentRial = installmentRial,
        startDate = startDate,
        notes = notes,
        linkedTxnRef = linkedTxnRef,
        createdAt = now,
        updatedAt = now,
    )
    val payments = (1..installmentCount).map { i ->
        // Space installments monthly from startDate.
        val dueDate = startDate + (i - 1).toLong() * 30L * 24 * 60 * 60 * 1000
        InstallmentPayment(
            id = "${id}_p$i",
            obligationId = id,
            installmentNumber = i,
            dueDate = dueDate,
            amountRial = installmentRial,
        )
    }
    return obligation to payments
}
