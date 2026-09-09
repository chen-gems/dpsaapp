package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActualShiftEntity
import com.example.data.AppDatabase
import com.example.data.BillingShiftEntity
import com.example.data.DpsaMonthSummaryEntity
import com.example.data.PlannedShiftEntity
import com.example.data.ShiftRepository
import com.example.model.DayReconciliation
import com.example.model.JaSalzburgRules
import com.example.model.MonthOverviewItem
import com.example.model.MonthlyReconciliationSummary
import com.example.model.ReconciliationCalculator
import com.example.model.ReconciliationStatus
import com.example.model.SixMonthsSummary
import com.example.model.TwelveMonthsSummary
import com.example.parser.HtmlScheduleParser
import com.example.parser.SampleHtmlData
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DiscrepancyFilter {
    ALL,
    DISCREPANCIES_ONLY,
    OVERTIME_ONLY
}

data class ReconciliationUiState(
    val selectedMonth: String = "2026-08",
    val filterType: DiscrepancyFilter = DiscrepancyFilter.ALL,
    val reconciliations: List<DayReconciliation> = emptyList(),
    val summary: MonthlyReconciliationSummary? = null,
    val dpsaSummary: DpsaMonthSummaryEntity? = null,
    val availableMonths: List<String> = emptyList(),
    val totalPlannedCount: Int = 0,
    val totalActualCount: Int = 0,
    val totalBillingCount: Int = 0,
    val sixMonthsList: List<MonthOverviewItem> = emptyList(),
    val sixMonthsSummary: SixMonthsSummary? = null,
    val twelveMonthsList: List<MonthOverviewItem> = emptyList(),
    val twelveMonthsSummary: TwelveMonthsSummary? = null
)

private data class RawShiftsData(
    val planned: List<PlannedShiftEntity>,
    val actual: List<ActualShiftEntity>,
    val billing: List<BillingShiftEntity>,
    val dpsaSummary: DpsaMonthSummaryEntity?,
    val allDpsaSummaries: List<DpsaMonthSummaryEntity>
)

class ShiftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ShiftRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ShiftRepository(db.shiftDao())
    }

    private val _selectedMonth = MutableStateFlow("2026-08")
    val selectedMonth: StateFlow<String> = _selectedMonth

    private val _filterType = MutableStateFlow(DiscrepancyFilter.ALL)
    val filterType: StateFlow<DiscrepancyFilter> = _filterType

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    val plannedShifts: StateFlow<List<PlannedShiftEntity>> = repository.allPlannedShifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actualShifts: StateFlow<List<ActualShiftEntity>> = repository.allActualShifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val billingShifts: StateFlow<List<BillingShiftEntity>> = repository.allBillingShifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentDpsaSummary: StateFlow<DpsaMonthSummaryEntity?> = _selectedMonth
        .flatMapLatest { month -> repository.getDpsaSummaryForMonth(month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allDpsaSummaries: StateFlow<List<DpsaMonthSummaryEntity>> = repository.allDpsaSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val shiftsDataFlow = combine(
        plannedShifts,
        actualShifts,
        billingShifts,
        currentDpsaSummary,
        allDpsaSummaries
    ) { planned, actual, billing, dpsaSummary, allDpsa ->
        RawShiftsData(planned, actual, billing, dpsaSummary, allDpsa)
    }

    val uiState: StateFlow<ReconciliationUiState> = combine(
        _selectedMonth,
        _filterType,
        shiftsDataFlow
    ) { month, filter, rawData ->
        val plannedList = rawData.planned
        val actualList = rawData.actual
        val billingList = rawData.billing
        val dpsaSummary = rawData.dpsaSummary

        // Extract all available months from data
        val allDates = (plannedList.map { it.date } + actualList.map { it.date } + billingList.map { it.date })
        val months = allDates.mapNotNull {
            if (it.length >= 7) it.substring(0, 7) else null
        }.distinct().sortedDescending()

        val activeMonth = if (month.isEmpty() && months.isNotEmpty()) months.first() else month

        // Filter shifts by month if selected
        val pFiltered = if (activeMonth.isNotEmpty()) plannedList.filter { it.date.startsWith(activeMonth) } else plannedList
        val aFiltered = if (activeMonth.isNotEmpty()) actualList.filter { it.date.startsWith(activeMonth) } else actualList
        val bFiltered = if (activeMonth.isNotEmpty()) billingList.filter { it.date.startsWith(activeMonth) } else billingList

        val pByDate = pFiltered.associateBy { it.date }
        val aByDate = aFiltered.associateBy { it.date }
        val bByDate = bFiltered.associateBy { it.date }

        val datesInMonth = (pByDate.keys + aByDate.keys + bByDate.keys).sorted()

        val allReconciliations = datesInMonth.map { date ->
            ReconciliationCalculator.calculateDayReconciliation(
                date = date,
                planned = pByDate[date],
                actual = aByDate[date],
                billing = bByDate[date]
            )
        }

        val filteredReconciliations = when (filter) {
            DiscrepancyFilter.ALL -> allReconciliations
            DiscrepancyFilter.DISCREPANCIES_ONLY -> allReconciliations.filter {
                it.status != ReconciliationStatus.MATCH
            }
            DiscrepancyFilter.OVERTIME_ONLY -> allReconciliations.filter {
                it.actualOvertime > 0 || it.billedOvertime > 0 || it.actualJournalHours > 0 || it.billedJournalHours > 0
            }
        }

        val summary = ReconciliationCalculator.calculateMonthlySummary(activeMonth, allReconciliations)

        val (sixMonthsList, sixMonthsSummary) = ReconciliationCalculator.calculateSixMonthsOverview(
            referenceMonth = activeMonth,
            allPlanned = plannedList,
            allActual = actualList,
            allBilling = billingList,
            allDpsaSummaries = rawData.allDpsaSummaries
        )

        ReconciliationUiState(
            selectedMonth = activeMonth,
            filterType = filter,
            reconciliations = filteredReconciliations,
            summary = summary,
            dpsaSummary = dpsaSummary,
            availableMonths = if (months.isEmpty()) listOf("2026-08", "2026-09") else months,
            totalPlannedCount = plannedList.size,
            totalActualCount = actualList.size,
            totalBillingCount = billingList.size,
            sixMonthsList = sixMonthsList,
            sixMonthsSummary = sixMonthsSummary,
            twelveMonthsList = sixMonthsList,
            twelveMonthsSummary = sixMonthsSummary
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ReconciliationUiState()
    )

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun setFilterType(filter: DiscrepancyFilter) {
        _filterType.value = filter
    }

    // --- HTML Imports ---

    fun importPlannedScheduleHtml(htmlContent: String, fileName: String = "Dienstplan.html") {
        viewModelScope.launch {
            val dpsaResult = HtmlScheduleParser.parseDpsaFull(htmlContent, fileName)
            if (dpsaResult.plannedShifts.isNotEmpty()) {
                repository.insertPlannedShifts(dpsaResult.plannedShifts)
                if (dpsaResult.monthSummary != null) {
                    repository.insertDpsaSummary(dpsaResult.monthSummary)
                }
                val firstMonth = dpsaResult.detectedPeriod.ifEmpty { dpsaResult.plannedShifts.first().date.take(7) }
                _selectedMonth.value = firstMonth
                _userMessage.emit("${dpsaResult.plannedShifts.size} DPSA-Dienste für Monat $firstMonth erfolgreich importiert!")
                return@launch
            }

            val parsed = HtmlScheduleParser.parsePlannedSchedule(htmlContent, fileName)
            if (parsed.isNotEmpty()) {
                repository.insertPlannedShifts(parsed)
                val firstMonth = parsed.first().date.take(7)
                _selectedMonth.value = firstMonth
                _userMessage.emit("${parsed.size} geplante Dienste für $firstMonth importiert.")
            } else {
                _userMessage.emit("Keine gültigen Dienstplandaten in HTML gefunden.")
            }
        }
    }

    fun importBillingStatementHtml(htmlContent: String, fileName: String = "Abrechnung.html") {
        viewModelScope.launch {
            val dpsaResult = HtmlScheduleParser.parseDpsaFull(htmlContent, fileName)
            if (dpsaResult.billingShifts.isNotEmpty()) {
                repository.insertBillingShifts(dpsaResult.billingShifts)
                if (dpsaResult.monthSummary != null) {
                    repository.insertDpsaSummary(dpsaResult.monthSummary)
                }
                val firstMonth = dpsaResult.detectedPeriod.ifEmpty { dpsaResult.billingShifts.first().billingPeriod }
                _selectedMonth.value = firstMonth
                _userMessage.emit("${dpsaResult.billingShifts.size} DPSA-Abrechnungszeilen & Salden für Monat $firstMonth importiert!")
                return@launch
            }

            val currentMonth = _selectedMonth.value
            val parsed = HtmlScheduleParser.parseBillingStatement(htmlContent, fileName, currentMonth)
            if (parsed.isNotEmpty()) {
                repository.insertBillingShifts(parsed)
                val period = parsed.first().billingPeriod.ifEmpty { currentMonth }
                if (period.isNotEmpty()) _selectedMonth.value = period
                _userMessage.emit("${parsed.size} Abrechnungszeilen für $period importiert.")
            } else {
                _userMessage.emit("Keine gültigen Abrechnungsdaten in HTML gefunden.")
            }
        }
    }

    // --- Shift CRUD ---

    fun saveActualShift(shift: ActualShiftEntity) {
        viewModelScope.launch {
            if (shift.id == 0L) {
                repository.insertActualShift(shift)
                _userMessage.emit("Ist-Dienst für ${shift.date} gespeichert.")
            } else {
                repository.updateActualShift(shift)
                _userMessage.emit("Ist-Dienst für ${shift.date} aktualisiert.")
            }
        }
    }

    fun saveActualShift(
        id: Long = 0,
        date: String,
        startTime: String,
        endTime: String,
        actualHours: Double,
        breakMinutes: Int = 0,
        overtimeHours: Double = 0.0,
        journalHours: Double = 0.0,
        shiftCode: String = "D",
        dutyOrLocation: String = "JA Salzburg",
        surcharges: String = "",
        notes: String = ""
    ) {
        val shift = ActualShiftEntity(
            id = id,
            date = date,
            startTime = startTime,
            endTime = endTime,
            actualHours = actualHours,
            breakMinutes = breakMinutes,
            overtimeHours = overtimeHours,
            journalHours = journalHours,
            shiftCode = shiftCode,
            dutyOrLocation = dutyOrLocation,
            surcharges = surcharges,
            notes = notes,
            isConfirmed = true
        )
        saveActualShift(shift)
    }

    /**
     * Applies a shift change for a specific day:
     * - Shift code change (e.g. D -> VD) -> times & base hours calculated automatically according to JA Salzburg rules.
     * - Additional hours (Zusatzstunden / Überstunden) -> added on top of base hours.
     */
    fun applyShiftChange(
        date: String,
        newShiftCode: String,
        additionalHours: Double
    ) {
        viewModelScope.launch {
            val timing = JaSalzburgRules.getTimingForShift(newShiftCode, date)
            val baseHours = timing.activeHours
            val totalHours = baseHours + additionalHours
            val journalHours = timing.journalHours

            // Calculate adjusted end time if additional hours were entered
            val adjustedEndTime = if (additionalHours > 0) {
                try {
                    val parts = timing.endTime.split(":")
                    val totalMinutes = parts[0].toInt() * 60 + parts[1].toInt() + (additionalHours * 60).toInt()
                    val endH = (totalMinutes / 60) % 24
                    val endM = totalMinutes % 60
                    String.format(Locale.US, "%02d:%02d", endH, endM)
                } catch (_: Exception) {
                    timing.endTime
                }
            } else {
                timing.endTime
            }

            val existing = actualShifts.value.firstOrNull { it.date == date }
            val planned = plannedShifts.value.firstOrNull { it.date == date }

            val notes = buildString {
                if (planned != null && planned.shiftCode != newShiftCode) {
                    append("Plan: ${planned.shiftCode} ➔ Änd: $newShiftCode")
                } else {
                    append("Dienst: $newShiftCode")
                }
                if (additionalHours > 0) {
                    append(", Zusatz: +${String.format(Locale.GERMANY, "%.2f", additionalHours)}h")
                }
            }

            val actualShift = ActualShiftEntity(
                id = existing?.id ?: 0,
                date = date,
                startTime = timing.startTime,
                endTime = adjustedEndTime,
                actualHours = totalHours,
                breakMinutes = timing.breakMinutes,
                overtimeHours = additionalHours,
                journalHours = journalHours,
                shiftCode = newShiftCode,
                dutyOrLocation = planned?.dutyOrLocation ?: "JA Salzburg",
                surcharges = timing.surcharges,
                notes = notes,
                isConfirmed = true
            )
            repository.insertActualShift(actualShift)
            _userMessage.emit("Änderung für $date gespeichert ($newShiftCode, ${String.format(Locale.GERMANY, "%.2f", totalHours)}h).")
        }
    }

    fun deleteActualShift(id: Long) {
        viewModelScope.launch {
            repository.deleteActualShift(id)
            _userMessage.emit("Ist-Dienst gelöscht.")
        }
    }

    fun deletePlannedShift(id: Long) {
        viewModelScope.launch {
            repository.deletePlannedShift(id)
            _userMessage.emit("Geplanter Dienst gelöscht.")
        }
    }

    fun deleteBillingShift(id: Long) {
        viewModelScope.launch {
            repository.deleteBillingShift(id)
            _userMessage.emit("Abrechnungszeile gelöscht.")
        }
    }

    fun copyPlannedToActual(planned: PlannedShiftEntity) {
        viewModelScope.launch {
            repository.copyPlannedToActual(planned)
            _userMessage.emit("Dienst am ${planned.date} (${planned.shiftCode}) in Ist-Zeiten übernommen.")
        }
    }

    fun copyAllPlannedToActual(plannedList: List<PlannedShiftEntity>) {
        viewModelScope.launch {
            if (plannedList.isEmpty()) {
                _userMessage.emit("Keine geplanten Dienste vorhanden.")
                return@launch
            }
            val copied = repository.copyAllPlannedToActual(plannedList)
            _userMessage.emit("$copied geplante Dienste in Ist-Zeiten übernommen.")
        }
    }

    fun copyAllPlannedToActualForCurrentMonth() {
        viewModelScope.launch {
            val month = _selectedMonth.value
            val currentPlanned = plannedShifts.value.filter { it.date.startsWith(month) }
            copyAllPlannedToActual(currentPlanned)
        }
    }

    fun clearPlannedShifts() {
        viewModelScope.launch {
            repository.clearPlannedShifts()
            _userMessage.emit("Geplante Dienste gelöscht.")
        }
    }

    fun clearActualShifts() {
        viewModelScope.launch {
            repository.clearActualShifts()
            _userMessage.emit("Erfasste Ist-Dienste gelöscht.")
        }
    }

    fun clearBillingShifts() {
        viewModelScope.launch {
            repository.clearBillingShifts()
            repository.clearAllDpsaSummaries()
            _userMessage.emit("Abrechnungsdaten und Salden gelöscht.")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearPlannedShifts()
            repository.clearActualShifts()
            repository.clearBillingShifts()
            repository.clearAllDpsaSummaries()
            _userMessage.emit("Alle Daten wurden zurückgesetzt.")
        }
    }

    /**
     * Preloads authentic DPSA data for August 2026 (JA Salzburg, Chen Shang Tun):
     * - August 2026 DPSA Dienststreifen
     * - August 2026 DPSA Abrechnung mit Salden (ZGS §82b, FZA 28,75h, Überstunden 16h)
     * - Corresponding actual shifts reflecting the real shifts worked.
     */
    fun loadDemoData() {
        viewModelScope.launch {
            val plannedDpsa = HtmlScheduleParser.parseDpsaFull(
                SampleHtmlData.samplePlannedScheduleHtml,
                "Dienst-Streifen-08-2026.html"
            )
            val billingDpsa = HtmlScheduleParser.parseDpsaFull(
                SampleHtmlData.sampleBillingHtml,
                "Stunden-Abrechnung-08-2026.html"
            )

            repository.clearAllDpsaSummaries()
            repository.clearPlannedShifts()
            repository.clearActualShifts()
            repository.clearBillingShifts()

            repository.insertPlannedShifts(plannedDpsa.plannedShifts)
            repository.insertBillingShifts(billingDpsa.billingShifts)
            if (billingDpsa.monthSummary != null) {
                repository.insertDpsaSummary(billingDpsa.monthSummary)
            }

            // Create actual shifts mirroring actual hours worked in August 2026:
            val actualList = mutableListOf<ActualShiftEntity>()
            for (b in billingDpsa.billingShifts) {
                if (b.billedHours > 0 || b.journalHours > 0) {
                    actualList.add(
                        ActualShiftEntity(
                            date = b.date,
                            startTime = b.billedStartTime,
                            endTime = b.billedEndTime,
                            actualHours = b.billedHours,
                            breakMinutes = 0, // JA Salzburg: keine Pause
                            overtimeHours = b.billedOvertimeHours,
                            journalHours = b.journalHours,
                            shiftCode = b.shiftCode,
                            dutyOrLocation = "JA Salzburg",
                            surcharges = b.billedSurcharges,
                            notes = if (b.billedOvertimeHours > 0) "${HtmlScheduleParser.formatDecimalAsHours(b.billedOvertimeHours)}h Überstunden geleistet" else "Geleistet",
                            isConfirmed = true
                        )
                    )
                }
            }

            repository.insertActualShifts(actualList)
            _selectedMonth.value = "2026-08"
            _userMessage.emit("Reale DPSA-Dienstdaten für JA Salzburg (August 2026) geladen.")
        }
    }

    fun loadSeptember2026Demo() {
        viewModelScope.launch {
            val plannedDpsa = HtmlScheduleParser.parseDpsaFull(
                SampleHtmlData.sampleSeptember2026ScheduleHtml,
                "Dienst-Streifen-09-2026.html"
            )
            repository.insertPlannedShifts(plannedDpsa.plannedShifts)
            if (plannedDpsa.monthSummary != null) {
                repository.insertDpsaSummary(plannedDpsa.monthSummary)
            }
            _selectedMonth.value = "2026-09"
            _userMessage.emit("DPSA Dienst-Streifen für September 2026 importiert!")
        }
    }
}
