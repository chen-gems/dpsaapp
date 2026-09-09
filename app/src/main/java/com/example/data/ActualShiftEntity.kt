package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "actual_shifts")
data class ActualShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // Format: YYYY-MM-DD
    val startTime: String = "", // HH:mm
    val endTime: String = "", // HH:mm
    val actualHours: Double = 0.0,
    val breakMinutes: Int = 0, // In JA Salzburg gibt es standardmäßig keine Pause (0 Min)
    val overtimeHours: Double = 0.0, // Geleistete Überstunden
    val journalHours: Double = 0.0, // Journaldienst (22:00 - 06:00 Uhr)
    val shiftCode: String = "", // z.B. D, ND, NF, DB, VD, TD
    val dutyOrLocation: String = "",
    val surcharges: String = "", // z.B. SF-Zulage, Nachtzuschlag
    val notes: String = "",
    val isConfirmed: Boolean = true
)
