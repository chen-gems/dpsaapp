package com.example.model

import com.example.data.ActualShiftEntity
import com.example.data.BillingShiftEntity
import com.example.data.DpsaMonthSummaryEntity
import com.example.data.PlannedShiftEntity
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class ReconciliationStatus {
    MATCH,                // Alles stimmt überein (Plan, Ist, Abrechnung)
    DEVIATION_HOURS,      // Stunden-Abweichung zwischen Ist und Abrechnung/Plan
    MISSING_IN_BILLING,   // Schicht gearbeitet, fehlt aber in der Abrechnung!
    OVERTIME_MISMATCH,    // Überstunden weichen in der Abrechnung ab
    JOURNAL_MISMATCH,     // Journaldienst-Stunden weichen ab
    PLANNED_NOT_LOGGED,   // Dienst war vorgeplant, aber noch nicht erfasst
    EXTRA_UNPLANNED_SHIFT // Gearbeitet, war aber nicht im Dienstplan
}

data class DayReconciliation(
    val date: String, // Format: YYYY-MM-DD
    val planned: PlannedShiftEntity?,
    val actual: ActualShiftEntity?,
    val billing: BillingShiftEntity?,
    val status: ReconciliationStatus,
    val shiftCode: String,
    val plannedHours: Double,
    val actualHours: Double,
    val billedHours: Double,
    val actualOvertime: Double,
    val billedOvertime: Double,
    val plannedJournalHours: Double = 0.0,
    val actualJournalHours: Double = 0.0,
    val billedJournalHours: Double = 0.0,
    val absenceHours: Double = 0.0,
    val hourDiffBilledVsActual: Double, // billing - actual (negativ = Abrechnung fehlt Zeit!)
    val overtimeDiffBilledVsActual: Double, // billing - actual
    val journalDiffBilledVsActual: Double = 0.0,
    val isDayOff: Boolean = false,
    val issues: List<String>
)

data class MonthlyReconciliationSummary(
    val month: String, // z.B. "2026-08"
    val totalDaysWithData: Int,
    val totalPlannedHours: Double,
    val totalActualHours: Double,
    val totalActualOvertime: Double,
    val totalBilledHours: Double,
    val totalBilledOvertime: Double,
    val totalPlannedJournalHours: Double = 0.0,
    val totalActualJournalHours: Double = 0.0,
    val totalBilledJournalHours: Double = 0.0,
    val netHoursDiscrepancy: Double, // actualHours - billedHours (fehlende Stunden)
    val netOvertimeDiscrepancy: Double, // actualOvertime - billedOvertime
    val perfectMatchCount: Int,
    val discrepancyCount: Int,
    val missingBillingCount: Int
)

data class MonthOverviewItem(
    val period: String, // "YYYY-MM", e.g. "2026-08"
    val displayName: String, // "August 2026"
    val plannedHours: Double = 0.0,
    val actualHours: Double = 0.0,
    val billedHours: Double = 0.0,
    val actualOvertime: Double = 0.0,
    val billedOvertime: Double = 0.0,
    val actualJournalHours: Double = 0.0,
    val billedJournalHours: Double = 0.0,
    val discrepancyCount: Int = 0,
    val missingBillingCount: Int = 0,
    val changesCount: Int = 0,
    val diffPlannedVsActual: Double = 0.0,
    val diffActualVsBilled: Double = 0.0,
    val diffPlannedVsBilled: Double = 0.0,
    val fzaBalance: Double? = null,
    val zgsBalance: Double? = null,
    val sfZulage: Double? = null,
    val dutyCount: Int = 0,
    val hasData: Boolean = false,
    val isCurrentMonth: Boolean = false
)

data class SixMonthsSummary(
    val totalPlannedHours: Double = 0.0,
    val totalActualHours: Double = 0.0,
    val totalBilledHours: Double = 0.0,
    val totalActualOvertime: Double = 0.0,
    val totalBilledOvertime: Double = 0.0,
    val totalActualJournal: Double = 0.0,
    val totalBilledJournal: Double = 0.0,
    val totalDiffPlannedVsActual: Double = 0.0, // Dienständerungen (Ist - Soll)
    val netDiscrepancyHours: Double = 0.0,      // Abrechnungsdifferenz (Ist - Abrechnung)
    val totalDiscrepanciesCount: Int = 0,
    val totalChangesCount: Int = 0,
    val currentFzaBalance: Double? = null,
    val currentZgsBalance: Double? = null,
    val currentSfZulage: Double? = null,
    val monthsWithDataCount: Int = 0
)

typealias TwelveMonthsSummary = SixMonthsSummary

object ReconciliationCalculator {

    fun calculateDayReconciliation(
        date: String,
        planned: PlannedShiftEntity?,
        actual: ActualShiftEntity?,
        billing: BillingShiftEntity?
    ): DayReconciliation {
        val pHours = planned?.plannedHours ?: 0.0
        val aHours = actual?.actualHours ?: 0.0
        val bHours = billing?.billedHours ?: 0.0

        val aOvertime = actual?.overtimeHours ?: 0.0
        val bOvertime = billing?.billedOvertimeHours ?: 0.0

        val pJournal = planned?.journalHours ?: 0.0
        val aJournal = actual?.journalHours ?: 0.0
        val bJournal = billing?.journalHours ?: 0.0

        val absence = planned?.absenceHours ?: billing?.absenceHours ?: 0.0

        val code = actual?.shiftCode?.ifEmpty { null }
            ?: billing?.shiftCode?.ifEmpty { null }
            ?: planned?.shiftCode ?: ""

        val isOff = (code == "--" || code == "--W" || code == "E") && (pHours == 0.0 && aHours == 0.0 && bHours == 0.0)

        val issues = mutableListOf<String>()

        val hourDiff = if (billing != null && actual != null) bHours - aHours else 0.0
        val overtimeDiff = if (billing != null && actual != null) bOvertime - aOvertime else 0.0
        val journalDiff = if (billing != null && actual != null) bJournal - aJournal else 0.0

        val status: ReconciliationStatus

        when {
            // Case 1: Both actual and billing exist (with or without plan)
            actual != null && billing != null -> {
                val hasHourDiff = abs(bHours - aHours) > 0.05
                val hasOvertimeDiff = abs(bOvertime - aOvertime) > 0.05
                val hasJournalDiff = abs(bJournal - aJournal) > 0.05

                if (hasHourDiff) {
                    val sign = if (bHours < aHours) "zu wenig" else "mehr als geleistet"
                    issues.add("Abrechnung weicht ab: ${formatHours(bHours)} Std. abgerechnet vs. ${formatHours(aHours)} Std. Ist ($sign)")
                }
                if (hasOvertimeDiff) {
                    issues.add("Überstunden abweichend: ${formatHours(bOvertime)} Std. abgerechnet vs. ${formatHours(aOvertime)} Std. erfasst")
                }
                if (hasJournalDiff) {
                    issues.add("Journalstunden abweichend: ${formatHours(bJournal)} Std. abgerechnet vs. ${formatHours(aJournal)} Std. erfasst")
                }
                if (planned != null && abs(aHours - pHours) > 0.05 && !isOff) {
                    issues.add("Planabweichung: ${formatHours(aHours)} Std. Ist vs. ${formatHours(pHours)} Std. Soll")
                } else if (planned == null) {
                    issues.add("Zusatzschicht (nicht vorgeplant)")
                }

                status = when {
                    hasHourDiff -> ReconciliationStatus.DEVIATION_HOURS
                    hasOvertimeDiff -> ReconciliationStatus.OVERTIME_MISMATCH
                    hasJournalDiff -> ReconciliationStatus.JOURNAL_MISMATCH
                    planned == null -> ReconciliationStatus.EXTRA_UNPLANNED_SHIFT
                    else -> ReconciliationStatus.MATCH
                }
            }

            // Case 2: Actual shift exists, but missing in billing
            actual != null && billing == null -> {
                if (aHours > 0 || aOvertime > 0) {
                    status = ReconciliationStatus.MISSING_IN_BILLING
                    issues.add("Achtung: Dienst am $date (${formatHours(aHours)} Std.) fehlt in der Abrechnung!")
                    if (planned == null) {
                        issues.add("Zusatzschicht (nicht im vorgeplanten Dienstplan)")
                    }
                } else {
                    status = ReconciliationStatus.MATCH
                }
            }

            // Case 3: Planned exists, but no actual shift recorded yet
            planned != null && actual == null -> {
                if (pHours > 0 || planned.shiftCode.isNotEmpty()) {
                    status = ReconciliationStatus.PLANNED_NOT_LOGGED
                    issues.add("Vorgeplant (${formatHours(pHours)} Std. ${planned.shiftCode}), aber noch keine eigene Ist-Zeit erfasst")
                    if (billing != null) {
                        issues.add("Hinweis: In Abrechnung mit ${formatHours(bHours)} Std. aufgeführt")
                    }
                } else {
                    status = ReconciliationStatus.MATCH
                }
            }

            // Case 4: In billing, but not in actual
            billing != null && actual == null -> {
                if (bHours > 0) {
                    status = ReconciliationStatus.DEVIATION_HOURS
                    issues.add("In Abrechnung enthalten (${formatHours(bHours)} Std.), aber keine eigene Arbeitszeit eingetragen")
                } else {
                    status = ReconciliationStatus.MATCH
                }
            }

            else -> {
                status = ReconciliationStatus.MATCH
            }
        }

        return DayReconciliation(
            date = date,
            planned = planned,
            actual = actual,
            billing = billing,
            status = status,
            shiftCode = code,
            plannedHours = pHours,
            actualHours = aHours,
            billedHours = bHours,
            actualOvertime = aOvertime,
            billedOvertime = bOvertime,
            plannedJournalHours = pJournal,
            actualJournalHours = aJournal,
            billedJournalHours = bJournal,
            absenceHours = absence,
            hourDiffBilledVsActual = hourDiff,
            overtimeDiffBilledVsActual = overtimeDiff,
            journalDiffBilledVsActual = journalDiff,
            isDayOff = isOff,
            issues = issues
        )
    }

    fun calculateMonthlySummary(
        month: String,
        reconciliations: List<DayReconciliation>
    ): MonthlyReconciliationSummary {
        val totalPlanned = reconciliations.sumOf { it.plannedHours }
        val totalActual = reconciliations.sumOf { it.actualHours }
        val totalActualOt = reconciliations.sumOf { it.actualOvertime }
        val totalBilled = reconciliations.sumOf { it.billedHours }
        val totalBilledOt = reconciliations.sumOf { it.billedOvertime }

        val totalPlannedJournal = reconciliations.sumOf { it.plannedJournalHours }
        val totalActualJournal = reconciliations.sumOf { it.actualJournalHours }
        val totalBilledJournal = reconciliations.sumOf { it.billedJournalHours }

        val perfectMatches = reconciliations.count { it.status == ReconciliationStatus.MATCH }
        val discrepancies = reconciliations.count {
            it.status == ReconciliationStatus.DEVIATION_HOURS ||
                    it.status == ReconciliationStatus.OVERTIME_MISMATCH ||
                    it.status == ReconciliationStatus.JOURNAL_MISMATCH
        }
        val missingInBilling = reconciliations.count { it.status == ReconciliationStatus.MISSING_IN_BILLING }

        return MonthlyReconciliationSummary(
            month = month,
            totalDaysWithData = reconciliations.size,
            totalPlannedHours = totalPlanned,
            totalActualHours = totalActual,
            totalActualOvertime = totalActualOt,
            totalBilledHours = totalBilled,
            totalBilledOvertime = totalBilledOt,
            totalPlannedJournalHours = totalPlannedJournal,
            totalActualJournalHours = totalActualJournal,
            totalBilledJournalHours = totalBilledJournal,
            netHoursDiscrepancy = totalActual - totalBilled,
            netOvertimeDiscrepancy = totalActualOt - totalBilledOt,
            perfectMatchCount = perfectMatches,
            discrepancyCount = discrepancies,
            missingBillingCount = missingInBilling
        )
    }

    fun calculateSixMonthsOverview(
        referenceMonth: String,
        allPlanned: List<PlannedShiftEntity>,
        allActual: List<ActualShiftEntity>,
        allBilling: List<BillingShiftEntity>,
        allDpsaSummaries: List<DpsaMonthSummaryEntity>
    ): Pair<List<MonthOverviewItem>, SixMonthsSummary> {
        val baseYearMonth = try {
            YearMonth.parse(referenceMonth)
        } catch (_: Exception) {
            YearMonth.now()
        }

        val dpsaByPeriod = allDpsaSummaries.associateBy { it.period }
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)

        val monthItems = (0..5).map { offset ->
            val ym = baseYearMonth.minusMonths(offset.toLong())
            val period = ym.toString() // "YYYY-MM"
            val displayName = ym.format(monthFormatter)

            val pShifts = allPlanned.filter { it.date.startsWith(period) }
            val aShifts = allActual.filter { it.date.startsWith(period) }
            val bShifts = allBilling.filter { it.date.startsWith(period) || it.billingPeriod == period }
            val dpsa = dpsaByPeriod[period]

            val pHours = pShifts.sumOf { it.plannedHours }
            val aHours = aShifts.sumOf { it.actualHours }
            val bHours = bShifts.sumOf { it.billedHours }
            val aOt = aShifts.sumOf { it.overtimeHours }
            val bOt = bShifts.sumOf { it.billedOvertimeHours }
            val aJournal = aShifts.sumOf { it.journalHours }
            val bJournal = bShifts.sumOf { it.journalHours }

            val pByDate = pShifts.associateBy { it.date }
            val aByDate = aShifts.associateBy { it.date }
            val bByDate = bShifts.associateBy { it.date }
            val allDates = (pByDate.keys + aByDate.keys + bByDate.keys).distinct()

            val dayReconciliations = allDates.map { date ->
                calculateDayReconciliation(
                    date = date,
                    planned = pByDate[date],
                    actual = aByDate[date],
                    billing = bByDate[date]
                )
            }

            val discCount = dayReconciliations.count {
                it.status == ReconciliationStatus.DEVIATION_HOURS ||
                        it.status == ReconciliationStatus.OVERTIME_MISMATCH ||
                        it.status == ReconciliationStatus.JOURNAL_MISMATCH
            }
            val missingCount = dayReconciliations.count { it.status == ReconciliationStatus.MISSING_IN_BILLING }

            val changesCount = allDates.count { date ->
                val p = pByDate[date]
                val a = aByDate[date]
                if (p != null && a != null) {
                    p.shiftCode != a.shiftCode || Math.abs(p.plannedHours - a.actualHours) > 0.01 || a.overtimeHours > 0
                } else if (p == null && a != null && a.actualHours > 0) {
                    true
                } else false
            }

            val fza = dpsa?.folgemonatFza ?: dpsa?.vormonatFza
            val zgs = dpsa?.zgsOffenBer ?: dpsa?.vormonatZgsBer
            val sf = dpsa?.sfZulage

            val hasData = pShifts.isNotEmpty() || aShifts.isNotEmpty() || bShifts.isNotEmpty() || dpsa != null
            val totalDuties = maxOf(pShifts.size, aShifts.size, bShifts.size)

            MonthOverviewItem(
                period = period,
                displayName = displayName,
                plannedHours = pHours,
                actualHours = aHours,
                billedHours = bHours,
                actualOvertime = aOt,
                billedOvertime = bOt,
                actualJournalHours = aJournal,
                billedJournalHours = bJournal,
                discrepancyCount = discCount,
                missingBillingCount = missingCount,
                changesCount = changesCount,
                diffPlannedVsActual = aHours - pHours,
                diffActualVsBilled = aHours - bHours,
                diffPlannedVsBilled = pHours - bHours,
                fzaBalance = fza,
                zgsBalance = zgs,
                sfZulage = sf,
                dutyCount = totalDuties,
                hasData = hasData,
                isCurrentMonth = period == referenceMonth
            )
        }

        // Summary across the 6 months
        val monthsWithData = monthItems.filter { it.hasData }
        val latestDpsa = monthItems.firstOrNull { it.fzaBalance != null || it.zgsBalance != null }

        val summary = SixMonthsSummary(
            totalPlannedHours = monthItems.sumOf { it.plannedHours },
            totalActualHours = monthItems.sumOf { it.actualHours },
            totalBilledHours = monthItems.sumOf { it.billedHours },
            totalActualOvertime = monthItems.sumOf { it.actualOvertime },
            totalBilledOvertime = monthItems.sumOf { it.billedOvertime },
            totalActualJournal = monthItems.sumOf { it.actualJournalHours },
            totalBilledJournal = monthItems.sumOf { it.billedJournalHours },
            totalDiffPlannedVsActual = monthItems.sumOf { it.diffPlannedVsActual },
            netDiscrepancyHours = monthItems.sumOf { it.actualHours } - monthItems.sumOf { it.billedHours },
            totalDiscrepanciesCount = monthItems.sumOf { it.discrepancyCount + it.missingBillingCount },
            totalChangesCount = monthItems.sumOf { it.changesCount },
            currentFzaBalance = latestDpsa?.fzaBalance,
            currentZgsBalance = latestDpsa?.zgsBalance,
            currentSfZulage = latestDpsa?.sfZulage,
            monthsWithDataCount = monthsWithData.size
        )

        return Pair(monthItems, summary)
    }

    fun calculateTwelveMonthsOverview(
        referenceMonth: String,
        allPlanned: List<PlannedShiftEntity>,
        allActual: List<ActualShiftEntity>,
        allBilling: List<BillingShiftEntity>,
        allDpsaSummaries: List<DpsaMonthSummaryEntity>
    ): Pair<List<MonthOverviewItem>, SixMonthsSummary> =
        calculateSixMonthsOverview(referenceMonth, allPlanned, allActual, allBilling, allDpsaSummaries)

    fun formatHours(hours: Double): String {
        return String.format(Locale.GERMANY, "%.2f", hours)
    }
}
