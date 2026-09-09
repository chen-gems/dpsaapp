package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "billing_shifts")
data class BillingShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // Format: YYYY-MM-DD
    val shiftCode: String = "",
    val billedStartTime: String = "",
    val billedEndTime: String = "",
    val plannedHours: Double = 0.0,
    val billedHours: Double = 0.0,
    val billedOvertimeHours: Double = 0.0,
    val journalHours: Double = 0.0,
    val absenceHours: Double = 0.0,
    val totalHours: Double = 0.0,
    val billedSurcharges: String = "", // z.B. SF-Zulage, Nacht, Gefahrenzulage
    val billedAmountEur: Double? = null,
    val notes: String = "",
    val billingPeriod: String = "", // Format: YYYY-MM
    val sourceFileName: String = ""
)
