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
 * A manual reservation of funds — money set aside for a specific purpose that should not
 * be counted as available to spend. Unlike installments, reservations have no repayment
 * schedule; they are a mental allocation the user manages manually.
 *
 * Optional [categoryId] links the reservation to a spending category for display purposes.
 * The reservation reduces the available balance until manually released.
 */
@Entity(
    tableName = "reservation",
    indices = [Index("released"), Index("created_at")],
)
data class Reservation(
    @PrimaryKey val id: String,
    /** Purpose of the reservation (e.g. "هزینه رانندگی"). */
    val purpose: String,
    /** Amount reserved in whole Rial. */
    @ColumnInfo(name = "amount_rial") val amountRial: Long,
    /** Optional link to a category for display. */
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** True when the reservation has been released (money returned to available). */
    val released: Boolean = false,
)

@Dao
interface ReservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(reservation: Reservation)

    @Query("SELECT * FROM reservation WHERE released = 0 ORDER BY created_at DESC")
    suspend fun active(): List<Reservation>

    @Query("SELECT * FROM reservation ORDER BY created_at DESC")
    suspend fun all(): List<Reservation>

    @Query("SELECT * FROM reservation WHERE id = :id")
    suspend fun get(id: String): Reservation?

    @Query("UPDATE reservation SET released = 1, updated_at = :now WHERE id = :id")
    suspend fun release(id: String, now: Long)

    @Query("UPDATE reservation SET released = 0, updated_at = :now WHERE id = :id")
    suspend fun unrelease(id: String, now: Long)

    @Query("DELETE FROM reservation WHERE id = :id")
    suspend fun delete(id: String)
}
