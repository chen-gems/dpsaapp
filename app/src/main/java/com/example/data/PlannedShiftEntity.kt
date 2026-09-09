package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planned_shifts")
data class PlannedShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // Format: YYYY-MM-DD
    val startTime: String = "", // HH:mm
    val endTime: String = "", // HH:mm
    val plannedHours: Double = 0.0,
    val breakMinutes: Int = 0,
    val shiftCode: String = "", // z.B. D, ND, NF, DB, VD, TD, U
    val dutyOrLocation: String = "", // z.B. JA Salzburg
    val notes: String = "",
    val journalHours: Double = 0.0,
    val absenceHours: Double = 0.0,
    val totalHours: Double = 0.0,
    val sourceFileName: String = ""
)
