package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {

    // --- Planned Shifts (Dienstplan) ---
    @Query("SELECT * FROM planned_shifts ORDER BY date ASC, startTime ASC")
    fun getAllPlannedShifts(): Flow<List<PlannedShiftEntity>>

    @Query("SELECT * FROM planned_shifts WHERE date LIKE :monthPrefix || '%' ORDER BY date ASC, startTime ASC")
    fun getPlannedShiftsForMonth(monthPrefix: String): Flow<List<PlannedShiftEntity>>

    @Query("SELECT * FROM planned_shifts WHERE date = :date LIMIT 1")
    suspend fun getPlannedShiftForDate(date: String): PlannedShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedShifts(shifts: List<PlannedShiftEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedShift(shift: PlannedShiftEntity): Long

    @Update
    suspend fun updatePlannedShift(shift: PlannedShiftEntity)

    @Query("DELETE FROM planned_shifts WHERE id = :id")
    suspend fun deletePlannedShiftById(id: Long)

    @Query("DELETE FROM planned_shifts WHERE date LIKE :monthPrefix || '%'")
    suspend fun clearPlannedShiftsForMonth(monthPrefix: String)

    @Query("DELETE FROM planned_shifts")
    suspend fun clearAllPlannedShifts()

    // --- Actual Shifts (Eigene Dienststunden) ---
    @Query("SELECT * FROM actual_shifts ORDER BY date ASC, startTime ASC")
    fun getAllActualShifts(): Flow<List<ActualShiftEntity>>

    @Query("SELECT * FROM actual_shifts WHERE date LIKE :monthPrefix || '%' ORDER BY date ASC, startTime ASC")
    fun getActualShiftsForMonth(monthPrefix: String): Flow<List<ActualShiftEntity>>

    @Query("SELECT * FROM actual_shifts WHERE date = :date LIMIT 1")
    suspend fun getActualShiftForDate(date: String): ActualShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActualShift(shift: ActualShiftEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActualShifts(shifts: List<ActualShiftEntity>)

    @Update
    suspend fun updateActualShift(shift: ActualShiftEntity)

    @Query("DELETE FROM actual_shifts WHERE id = :id")
    suspend fun deleteActualShiftById(id: Long)

    @Query("DELETE FROM actual_shifts WHERE date LIKE :monthPrefix || '%'")
    suspend fun clearActualShiftsForMonth(monthPrefix: String)

    @Query("DELETE FROM actual_shifts")
    suspend fun clearAllActualShifts()

    // --- Billing Shifts (Abrechnung) ---
    @Query("SELECT * FROM billing_shifts ORDER BY date ASC, billedStartTime ASC")
    fun getAllBillingShifts(): Flow<List<BillingShiftEntity>>

    @Query("SELECT * FROM billing_shifts WHERE date LIKE :monthPrefix || '%' ORDER BY date ASC, billedStartTime ASC")
    fun getBillingShiftsForMonth(monthPrefix: String): Flow<List<BillingShiftEntity>>

    @Query("SELECT * FROM billing_shifts WHERE date = :date LIMIT 1")
    suspend fun getBillingShiftForDate(date: String): BillingShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillingShifts(shifts: List<BillingShiftEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillingShift(shift: BillingShiftEntity): Long

    @Update
    suspend fun updateBillingShift(shift: BillingShiftEntity)

    @Query("DELETE FROM billing_shifts WHERE id = :id")
    suspend fun deleteBillingShiftById(id: Long)

    @Query("DELETE FROM billing_shifts WHERE date LIKE :monthPrefix || '%'")
    suspend fun clearBillingShiftsForMonth(monthPrefix: String)

    @Query("DELETE FROM billing_shifts")
    suspend fun clearAllBillingShifts()

    // --- DPSA Month Summaries (Salden, ZGS §82b, FZA, Zulagen) ---
    @Query("SELECT * FROM dpsa_month_summaries WHERE period = :period LIMIT 1")
    fun getDpsaSummaryForMonth(period: String): Flow<DpsaMonthSummaryEntity?>

    @Query("SELECT * FROM dpsa_month_summaries ORDER BY period DESC")
    fun getAllDpsaSummaries(): Flow<List<DpsaMonthSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDpsaSummary(summary: DpsaMonthSummaryEntity)

    @Query("DELETE FROM dpsa_month_summaries WHERE period = :period")
    suspend fun deleteDpsaSummaryForMonth(period: String)

    @Query("DELETE FROM dpsa_month_summaries")
    suspend fun clearAllDpsaSummaries()
}
