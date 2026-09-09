package com.example.data

import kotlinx.coroutines.flow.Flow

class ShiftRepository(private val dao: ShiftDao) {

    val allPlannedShifts: Flow<List<PlannedShiftEntity>> = dao.getAllPlannedShifts()
    val allActualShifts: Flow<List<ActualShiftEntity>> = dao.getAllActualShifts()
    val allBillingShifts: Flow<List<BillingShiftEntity>> = dao.getAllBillingShifts()

    fun getPlannedShiftsForMonth(monthPrefix: String): Flow<List<PlannedShiftEntity>> =
        dao.getPlannedShiftsForMonth(monthPrefix)

    fun getActualShiftsForMonth(monthPrefix: String): Flow<List<ActualShiftEntity>> =
        dao.getActualShiftsForMonth(monthPrefix)

    fun getBillingShiftsForMonth(monthPrefix: String): Flow<List<BillingShiftEntity>> =
        dao.getBillingShiftsForMonth(monthPrefix)

    suspend fun insertPlannedShifts(shifts: List<PlannedShiftEntity>) = dao.insertPlannedShifts(shifts)
    suspend fun insertPlannedShift(shift: PlannedShiftEntity) = dao.insertPlannedShift(shift)
    suspend fun updatePlannedShift(shift: PlannedShiftEntity) = dao.updatePlannedShift(shift)
    suspend fun deletePlannedShift(id: Long) = dao.deletePlannedShiftById(id)
    suspend fun clearPlannedShifts() = dao.clearAllPlannedShifts()

    suspend fun insertActualShift(shift: ActualShiftEntity) = dao.insertActualShift(shift)
    suspend fun insertActualShifts(shifts: List<ActualShiftEntity>) = dao.insertActualShifts(shifts)
    suspend fun updateActualShift(shift: ActualShiftEntity) = dao.updateActualShift(shift)
    suspend fun deleteActualShift(id: Long) = dao.deleteActualShiftById(id)
    suspend fun clearActualShifts() = dao.clearAllActualShifts()

    suspend fun insertBillingShifts(shifts: List<BillingShiftEntity>) = dao.insertBillingShifts(shifts)
    suspend fun insertBillingShift(shift: BillingShiftEntity) = dao.insertBillingShift(shift)
    suspend fun updateBillingShift(shift: BillingShiftEntity) = dao.updateBillingShift(shift)
    suspend fun deleteBillingShift(id: Long) = dao.deleteBillingShiftById(id)
    suspend fun clearBillingShifts() = dao.clearAllBillingShifts()

    // --- DPSA Month Summaries ---
    fun getDpsaSummaryForMonth(period: String): Flow<DpsaMonthSummaryEntity?> =
        dao.getDpsaSummaryForMonth(period)

    val allDpsaSummaries: Flow<List<DpsaMonthSummaryEntity>> =
        dao.getAllDpsaSummaries()

    suspend fun insertDpsaSummary(summary: DpsaMonthSummaryEntity) =
        dao.insertDpsaSummary(summary)

    suspend fun deleteDpsaSummary(period: String) =
        dao.deleteDpsaSummaryForMonth(period)

    suspend fun clearAllDpsaSummaries() =
        dao.clearAllDpsaSummaries()

    /**
     * Copies a planned shift into the actual shift table so the user
     * can confirm or adjust what was actually worked without typing from scratch.
     */
    suspend fun copyPlannedToActual(planned: PlannedShiftEntity): Long {
        val existing = dao.getActualShiftForDate(planned.date)
        val actual = ActualShiftEntity(
            id = existing?.id ?: 0,
            date = planned.date,
            startTime = planned.startTime,
            endTime = planned.endTime,
            actualHours = planned.plannedHours,
            breakMinutes = planned.breakMinutes,
            overtimeHours = 0.0,
            journalHours = planned.journalHours,
            shiftCode = planned.shiftCode,
            dutyOrLocation = planned.dutyOrLocation,
            surcharges = if (planned.journalHours > 0) "Journaldienst (${planned.journalHours.toInt()}h)" else "",
            notes = "Aus Dienstplan übernommen",
            isConfirmed = true
        )
        return dao.insertActualShift(actual)
    }

    /**
     * Bulk copy all planned shifts in a month to actual shifts if not yet logged.
     */
    suspend fun copyAllPlannedToActual(plannedList: List<PlannedShiftEntity>): Int {
        var count = 0
        for (planned in plannedList) {
            val existing = dao.getActualShiftForDate(planned.date)
            if (existing == null) {
                copyPlannedToActual(planned)
                count++
            }
        }
        return count
    }
}
