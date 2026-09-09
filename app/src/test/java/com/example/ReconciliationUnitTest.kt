package com.example

import com.example.data.ActualShiftEntity
import com.example.data.BillingShiftEntity
import com.example.model.JaSalzburgRules
import com.example.model.ReconciliationCalculator
import com.example.model.ReconciliationStatus
import com.example.parser.HtmlScheduleParser
import com.example.parser.SampleHtmlData
import org.junit.Assert.*
import org.junit.Test

class ReconciliationUnitTest {

    @Test
    fun testJaSalzburgShiftRules() {
        // Monday: D = 07:00-15:00 (8h, 0 break)
        val monD = JaSalzburgRules.getTimingForShift("D", "2026-08-03")
        assertEquals("07:00", monD.startTime)
        assertEquals("15:00", monD.endTime)
        assertEquals(8.0, monD.activeHours, 0.01)
        assertEquals(0, monD.breakMinutes)
        assertEquals(0.0, monD.journalHours, 0.01)

        // Friday: D = 07:00-13:00 (6h, 0 break)
        val friD = JaSalzburgRules.getTimingForShift("D", "2026-08-07")
        assertEquals("07:00", friD.startTime)
        assertEquals("13:00", friD.endTime)
        assertEquals(6.0, friD.activeHours, 0.01)
        assertEquals(0, friD.breakMinutes)

        // Saturday: D = 07:00-12:00 (5h, 0 break)
        val satD = JaSalzburgRules.getTimingForShift("D", "2026-08-08")
        assertEquals("07:00", satD.startTime)
        assertEquals("12:00", satD.endTime)
        assertEquals(5.0, satD.activeHours, 0.01)
        assertEquals(0, satD.breakMinutes)

        // Sunday: D = 07:00-12:00 (5h, 0 break)
        val sunD = JaSalzburgRules.getTimingForShift("D", "2026-08-09")
        assertEquals("07:00", sunD.startTime)
        assertEquals("12:00", sunD.endTime)
        assertEquals(5.0, sunD.activeHours, 0.01)

        // ND (Nachtdienst 24h): 07:00-22:00 (15h active + 2h journal am ND Tag)
        val nd = JaSalzburgRules.getTimingForShift("ND", "2026-08-10")
        assertEquals("07:00", nd.startTime)
        assertEquals("22:00", nd.endTime)
        assertEquals(15.0, nd.activeHours, 0.01)
        assertEquals(2.0, nd.journalHours, 0.01)

        // NF (Nachtfolgezeit): 06:00-07:00 (1h active + 6h journal am NF Tag)
        val nf = JaSalzburgRules.getTimingForShift("NF", "2026-08-11")
        assertEquals("06:00", nf.startTime)
        assertEquals("07:00", nf.endTime)
        assertEquals(1.0, nf.activeHours, 0.01)
        assertEquals(6.0, nf.journalHours, 0.01)

        // DB: 11 Stunden (07:00-18:00)
        val db = JaSalzburgRules.getTimingForShift("DB", "2026-08-12")
        assertEquals("07:00", db.startTime)
        assertEquals("18:00", db.endTime)
        assertEquals(11.0, db.activeHours, 0.01)

        // VD (Verlängerter Dienst): 10 Stunden (07:00-17:00) Mo-Do, 8 Stunden (07:00-15:00) Fr
        val vdMon = JaSalzburgRules.getTimingForShift("VD", "2026-08-03") // Mo
        assertEquals("07:00", vdMon.startTime)
        assertEquals("17:00", vdMon.endTime)
        assertEquals(10.0, vdMon.activeHours, 0.01)
        assertTrue(JaSalzburgRules.isVDAllowed("2026-08-03"))

        val vdThu = JaSalzburgRules.getTimingForShift("VD", "2026-08-06") // Do
        assertEquals("07:00", vdThu.startTime)
        assertEquals("17:00", vdThu.endTime)
        assertEquals(10.0, vdThu.activeHours, 0.01)
        assertTrue(JaSalzburgRules.isVDAllowed("2026-08-06"))

        val vdFri = JaSalzburgRules.getTimingForShift("VD", "2026-08-07") // Fr (07-15 Uhr, 8h)
        assertEquals("07:00", vdFri.startTime)
        assertEquals("15:00", vdFri.endTime)
        assertEquals(8.0, vdFri.activeHours, 0.01)
        assertTrue(JaSalzburgRules.isVDAllowed("2026-08-07"))

        // Sa, So und Feiertag: kein VD!
        assertFalse("Sa darf kein VD sein", JaSalzburgRules.isVDAllowed("2026-08-08"))
        assertFalse("So darf kein VD sein", JaSalzburgRules.isVDAllowed("2026-08-09"))
        assertFalse("Feiertag (15. Aug) darf kein VD sein", JaSalzburgRules.isVDAllowed("2026-08-15"))

        val vdSat = JaSalzburgRules.getTimingForShift("VD", "2026-08-08")
        assertEquals(0.0, vdSat.activeHours, 0.01)

        val vdHoliday = JaSalzburgRules.getTimingForShift("VD", "2026-08-15")
        assertEquals(0.0, vdHoliday.activeHours, 0.01)

        // TD: 5 Stunden (07:00-12:00)
        val td = JaSalzburgRules.getTimingForShift("TD", "2026-08-14")
        assertEquals("07:00", td.startTime)
        assertEquals("12:00", td.endTime)
        assertEquals(5.0, td.activeHours, 0.01)

        // 6-Tage-Woche Urlaub (U): 6.67h (06:40)
        val u = JaSalzburgRules.getTimingForShift("U", "2026-08-17")
        assertTrue(u.isAbsence)
        assertEquals(6.67, u.activeHours, 0.01)
    }

    @Test
    fun testParseDpsaAugustPlannedSchedule() {
        val result = HtmlScheduleParser.parseDpsaFull(
            SampleHtmlData.samplePlannedScheduleHtml,
            "Dienst-Streifen-08-2026.html"
        )
        assertTrue("Planned shifts should not be empty", result.plannedShifts.isNotEmpty())
        assertEquals("2026-08", result.detectedPeriod)

        // Check Day 1 (2026-08-01: Sa -> weekend off --W)
        val day1 = result.plannedShifts.find { it.date == "2026-08-01" }
        assertNotNull(day1)
        assertEquals("--W", day1?.shiftCode)
        assertEquals(0.0, day1?.plannedHours ?: 0.0, 0.01)

        // Check Monday (2026-08-03: Mo -> D 07:00-15:00 = 8h)
        val day3 = result.plannedShifts.find { it.date == "2026-08-03" }
        assertNotNull(day3)
        assertEquals("D", day3?.shiftCode)
        assertEquals(8.0, day3?.plannedHours ?: 0.0, 0.01)

        // Check Friday (2026-08-07: Fr -> D 07:00-13:00 = 6h)
        val day7 = result.plannedShifts.find { it.date == "2026-08-07" }
        assertNotNull(day7)
        assertEquals("D", day7?.shiftCode)
        assertEquals(6.0, day7?.plannedHours ?: 0.0, 0.01)

        // Check Saturday duty (2026-08-08: Sa -> D 07:00-12:00 = 5h)
        val day8 = result.plannedShifts.find { it.date == "2026-08-08" }
        assertNotNull(day8)
        assertEquals("D", day8?.shiftCode)
        assertEquals(5.0, day8?.plannedHours ?: 0.0, 0.01)

        // Check ND day (2026-08-10: ND -> 13h active + 2h journal in DPSA Dienststreifen)
        val day10 = result.plannedShifts.find { it.date == "2026-08-10" }
        assertNotNull(day10)
        assertEquals("ND", day10?.shiftCode)
        assertEquals(13.0, day10?.plannedHours ?: 0.0, 0.01)
        assertEquals(2.0, day10?.journalHours ?: 0.0, 0.01)

        // Check NF day (2026-08-11: NF -> 1h active + 6h journal)
        val day11 = result.plannedShifts.find { it.date == "2026-08-11" }
        assertNotNull(day11)
        assertEquals("NF", day11?.shiftCode)
        assertEquals(1.0, day11?.plannedHours ?: 0.0, 0.01)
        assertEquals(6.0, day11?.journalHours ?: 0.0, 0.01)
    }

    @Test
    fun testParseDpsaAugustBillingAndBalances() {
        val result = HtmlScheduleParser.parseDpsaFull(
            SampleHtmlData.sampleBillingHtml,
            "Stunden-Abrechnung-08-2026.html"
        )
        assertTrue("Billing shifts should not be empty", result.billingShifts.isNotEmpty())
        assertEquals("2026-08", result.detectedPeriod)

        val summary = result.monthSummary
        assertNotNull("DPSA Monthly Summary block must be parsed", summary)
        assertEquals("2026-08", summary?.period)
        assertEquals(179.67, summary?.sollStd ?: 0.0, 0.01)
        assertEquals(195.67, summary?.gesStd ?: 0.0, 0.01)
        assertEquals(16.0, summary?.ueberStd ?: 0.0, 0.01)
        assertEquals(32.0, summary?.journalDienstStd ?: 0.0, 0.01)
        assertEquals(12.75, summary?.vormonatFza ?: 0.0, 0.01)
        assertEquals(51.50, summary?.vormonatZgsBer ?: 0.0, 0.01)
    }

    @Test
    fun testReconciliationJournalMismatch() {
        val actual = ActualShiftEntity(
            date = "2026-08-10",
            startTime = "07:00",
            endTime = "22:00",
            actualHours = 15.0,
            journalHours = 2.0,
            shiftCode = "ND"
        )
        val billing = BillingShiftEntity(
            date = "2026-08-10",
            billedHours = 15.0,
            journalHours = 0.0, // Missing in billing!
            shiftCode = "ND"
        )

        val result = ReconciliationCalculator.calculateDayReconciliation(
            date = "2026-08-10",
            planned = null,
            actual = actual,
            billing = billing
        )

        assertEquals(ReconciliationStatus.JOURNAL_MISMATCH, result.status)
        assertTrue(result.issues.any { it.contains("Journalstunden") })
    }

    @Test
    fun testReconciliationOvertimeMismatch() {
        val actual = ActualShiftEntity(
            date = "2026-08-04",
            startTime = "07:00",
            endTime = "17:00",
            actualHours = 8.0,
            overtimeHours = 2.0,
            shiftCode = "D"
        )
        val billing = BillingShiftEntity(
            date = "2026-08-04",
            billedHours = 8.0,
            billedOvertimeHours = 0.0, // Not paid/billed as overtime!
            shiftCode = "D"
        )

        val result = ReconciliationCalculator.calculateDayReconciliation(
            date = "2026-08-04",
            planned = null,
            actual = actual,
            billing = billing
        )

        assertEquals(ReconciliationStatus.OVERTIME_MISMATCH, result.status)
        assertEquals(-2.0, result.overtimeDiffBilledVsActual, 0.01)
    }

    @Test
    fun testSixMonthsOverviewCalculation() {
        val plannedList = listOf(
            com.example.data.PlannedShiftEntity(
                date = "2026-08-03", // Mo: D
                shiftCode = "D",
                plannedHours = 8.0
            )
        )
        // Actual shift adjusted: changed from D to VD (Volldienst) + 1 extra hour = 11.0h
        val actualList = listOf(
            ActualShiftEntity(
                date = "2026-08-03",
                shiftCode = "VD",
                actualHours = 11.0,
                startTime = "07:00",
                endTime = "18:00"
            )
        )
        val billingList = listOf(
            BillingShiftEntity(
                date = "2026-08-03",
                shiftCode = "VD",
                billedHours = 11.0,
                billingPeriod = "2026-08"
            )
        )

        val (items, summary) = ReconciliationCalculator.calculateSixMonthsOverview(
            referenceMonth = "2026-08",
            allPlanned = plannedList,
            allActual = actualList,
            allBilling = billingList,
            allDpsaSummaries = emptyList()
        )

        assertEquals(6, items.size)
        val augItem = items.find { it.period == "2026-08" }
        assertNotNull(augItem)
        assertEquals(8.0, augItem?.plannedHours ?: 0.0, 0.01)
        assertEquals(11.0, augItem?.actualHours ?: 0.0, 0.01) // 11h
        assertEquals(11.0, augItem?.billedHours ?: 0.0, 0.01)
        assertEquals(1, augItem?.changesCount ?: 0) // was modified (D vs VD)
        assertEquals(3.0, augItem?.diffPlannedVsActual ?: 0.0, 0.01) // 11 - 8 = +3h
        assertEquals(0.0, augItem?.diffActualVsBilled ?: 0.0, 0.01) // 11 - 11 = 0h (stimmig nach Abrechnung)

        assertEquals(1, summary.totalChangesCount)
        assertEquals(0, summary.totalDiscrepanciesCount)
    }
}
