package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlannedShiftEntity
import com.example.model.JaSalzburgRules
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedScheduleScreen(
    plannedShifts: List<PlannedShiftEntity>,
    selectedMonth: String,
    onSelectMonth: (String) -> Unit = {},
    onOpenImport: () -> Unit,
    onCopySingleToActual: (PlannedShiftEntity) -> Unit,
    onCopyAllToActual: (List<PlannedShiftEntity>) -> Unit,
    onDeleteShift: (Long) -> Unit
) {
    var showCopyAllConfirmation by remember { mutableStateOf(false) }
    var isCalendarView by remember { mutableStateOf(true) } // Standard: Monatsansicht!
    var selectedDayShift by remember { mutableStateOf<PlannedShiftEntity?>(null) }
    var selectedDateForEmptyDay by remember { mutableStateOf<String?>(null) }

    val activeMonth = selectedMonth.ifEmpty { "2026-08" }

    val currentYm = remember(activeMonth) {
        try {
            YearMonth.parse(activeMonth)
        } catch (_: Exception) {
            YearMonth.now()
        }
    }

    val monthDisplayName = remember(currentYm) {
        currentYm.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY))
    }

    val monthFiltered = remember(plannedShifts, activeMonth) {
        plannedShifts.filter { it.date.startsWith(activeMonth) }
    }

    val shiftsByDate = remember(monthFiltered) {
        monthFiltered.associateBy { it.date }
    }

    val totalHours = remember(monthFiltered) {
        monthFiltered.sumOf { it.plannedHours }
    }

    val totalJournalHours = remember(monthFiltered) {
        monthFiltered.sumOf { it.journalHours }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("planned_schedule_screen")
    ) {
        // --- Month Navigation & View Switcher Bar ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Month Navigation Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val prev = currentYm.minusMonths(1).toString()
                            onSelectMonth(prev)
                        },
                        modifier = Modifier.testTag("prev_month_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voriger Monat")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = monthDisplayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${monthFiltered.size} Dienste • ${String.format(Locale.GERMANY, "%.2f", totalHours)} Soll-Std." +
                                    if (totalJournalHours > 0) " • ${String.format(Locale.GERMANY, "%.1f", totalJournalHours)}h Journal" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val next = currentYm.plusMonths(1).toString()
                            onSelectMonth(next)
                        },
                        modifier = Modifier.testTag("next_month_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Nächster Monat")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Import Button (nur funktion html import)
                Button(
                    onClick = onOpenImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("import_plan_btn"),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dienstplan HTML importieren", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // --- Main Content: Monatsansicht (Kalender) ---
        MonthCalendarView(
            yearMonth = currentYm,
            shiftsByDate = shiftsByDate,
            onDayClick = { date, shift ->
                if (shift != null) {
                    selectedDayShift = shift
                } else {
                    selectedDateForEmptyDay = date
                }
            }
        )
    }

    // Detail Dialog when clicking a day in the Monatsansicht
    selectedDayShift?.let { shift ->
        PlannedShiftDetailDialog(
            shift = shift,
            onDismiss = { selectedDayShift = null },
            onCopyToActual = {
                onCopySingleToActual(shift)
                selectedDayShift = null
            },
            onDelete = {
                onDeleteShift(shift.id)
                selectedDayShift = null
            }
        )
    }

    // Dialog when clicking a day with no shift
    selectedDateForEmptyDay?.let { date ->
        AlertDialog(
            onDismissRequest = { selectedDateForEmptyDay = null },
            title = { Text("Kein Dienst eingeteilt") },
            text = { Text("Am ${formatDisplayDate(date)} (${formatWeekday(date)}) ist kein Dienst im Dienstplan vorgeplant.") },
            confirmButton = {
                TextButton(onClick = { selectedDateForEmptyDay = null }) {
                    Text("OK")
                }
            }
        )
    }

    // Confirmation dialog for copying all shifts
    if (showCopyAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showCopyAllConfirmation = false },
            title = { Text("Dienstplan übernehmen?") },
            text = {
                Text("Möchtest du alle ${monthFiltered.size} vorgeplanten Dienste für $monthDisplayName in deine eigenen Ist-Zeiten übertragen? Bereits manuell erfasste Tage werden dabei nicht überschrieben.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCopyAllToActual(monthFiltered)
                        showCopyAllConfirmation = false
                    }
                ) {
                    Text("Ja, alle übernehmen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyAllConfirmation = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

/**
 * Clean, responsive 7-column calendar month view (Monatsansicht)
 * with weekday headers Mo, Di, Mi, Do, Fr, Sa, So.
 */
@Composable
private fun MonthCalendarView(
    yearMonth: YearMonth,
    shiftsByDate: Map<String, PlannedShiftEntity>,
    onDayClick: (String, PlannedShiftEntity?) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    // In Austria / ISO, Monday = 1, Sunday = 7
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value
    val leadingEmptyCells = firstDayOfWeek - 1
    val totalCells = leadingEmptyCells + daysInMonth
    val numRows = (totalCells + 6) / 7

    val weekdays = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 80.dp)
            .testTag("month_calendar_grid"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Weekday Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekdays.forEachIndexed { index, day ->
                    val isWeekend = index >= 5
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isWeekend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))

            // Calendar Grid Rows
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                for (row in 0 until numRows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val dayOfMonth = cellIndex - leadingEmptyCells + 1

                            if (dayOfMonth in 1..daysInMonth) {
                                val dateStr = String.format(Locale.US, "%04d-%02d-%02d", yearMonth.year, yearMonth.monthValue, dayOfMonth)
                                val shift = shiftsByDate[dateStr]
                                val isWeekend = col >= 5

                                CalendarDayCell(
                                    dayOfMonth = dayOfMonth,
                                    isWeekend = isWeekend,
                                    shift = shift,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onDayClick(dateStr, shift) }
                                )
                            } else {
                                // Empty placeholder cell
                                Box(modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayOfMonth: Int,
    isWeekend: Boolean,
    shift: PlannedShiftEntity?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cellBackground = if (shift != null) {
        getShiftBgColor(shift.shiftCode)
    } else if (isWeekend) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (shift != null) {
        getShiftBorderColor(shift.shiftCode)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .testTag("cal_day_$dayOfMonth"),
        shape = RoundedCornerShape(8.dp),
        color = cellBackground,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Day Number
            Text(
                text = "$dayOfMonth",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isWeekend || shift != null) FontWeight.Bold else FontWeight.Medium,
                color = if (isWeekend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start).padding(start = 2.dp)
            )

            // Shift Badge and Timing
            if (shift != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = getShiftBadgeColor(shift.shiftCode),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp)
                ) {
                    Text(
                        text = shift.shiftCode.ifEmpty { "D" },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }

                // Hours text (e.g. "8h" or "15h+2J")
                val hoursText = if (shift.journalHours > 0) {
                    "${formatShortHours(shift.plannedHours)}+${formatShortHours(shift.journalHours)}J"
                } else {
                    "${formatShortHours(shift.plannedHours)}h"
                }
                Text(
                    text = hoursText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Detailed bottom dialog when clicking on a shift in the Monatsansicht
 */
@Composable
private fun PlannedShiftDetailDialog(
    shift: PlannedShiftEntity,
    onDismiss: () -> Unit,
    onCopyToActual: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${formatDisplayDate(shift.date)} • ${formatWeekday(shift.date)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dienstplan-Einteilung",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Shift Code Badge & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = getShiftBadgeColor(shift.shiftCode),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = shift.shiftCode,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = getShiftDescription(shift.shiftCode, shift.date),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Working Times
                DetailRow(
                    label = "Arbeitszeit:",
                    value = "${shift.startTime} – ${shift.endTime} Uhr"
                )

                // Planned Active Hours
                DetailRow(
                    label = "Geplante Sollstunden:",
                    value = "${String.format(Locale.GERMANY, "%.2f", shift.plannedHours)} Std."
                )

                // Journaldienst
                if (shift.journalHours > 0) {
                    DetailRow(
                        label = "Journaldienst:",
                        value = "${String.format(Locale.GERMANY, "%.2f", shift.journalHours)} Std. (Bereitschaft)"
                    )
                }

                // Pause
                DetailRow(
                    label = "Pause:",
                    value = if (shift.breakMinutes == 0) "0 Min. (keine Pause lt. JA Salzburg)" else "${shift.breakMinutes} Min."
                )

                // Duty location / notes
                if (shift.dutyOrLocation.isNotEmpty()) {
                    DetailRow(
                        label = "Dienststelle:",
                        value = shift.dutyOrLocation
                    )
                }
                if (shift.notes.isNotEmpty()) {
                    DetailRow(
                        label = "Hinweis:",
                        value = shift.notes
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCopyToActual,
                modifier = Modifier.testTag("dialog_copy_to_actual_btn")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("In Ist-Zeiten übernehmen")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
                TextButton(onClick = onDismiss) {
                    Text("Schließen")
                }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun PlannedShiftCard(
    shift: PlannedShiftEntity,
    onCopyToActual: () -> Unit,
    onDelete: () -> Unit
) {
    val weekday = formatWeekday(shift.date)
    val displayDate = formatDisplayDate(shift.date)

    Card(
        modifier = Modifier.fillMaxWidth().testTag("planned_card_${shift.date}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getShiftBadgeColor(shift.shiftCode),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = shift.shiftCode.ifEmpty { weekday },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$displayDate ($weekday)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (shift.journalHours > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    "+${String.format(Locale.GERMANY, "%.0f", shift.journalHours)}h Journal",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${shift.startTime} – ${shift.endTime} • ${String.format(Locale.GERMANY, "%.2f", shift.plannedHours)} Std.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (shift.dutyOrLocation.isNotEmpty() || shift.notes.isNotEmpty()) {
                        Text(
                            text = listOf(shift.dutyOrLocation, shift.notes).filter { it.isNotEmpty() }.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopyToActual, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "In Ist übernehmen",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Löschen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlanState(onOpenImport: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Keine vorgeplanten Dienste für diesen Monat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Lade die Dienstplan-HTML-Datei der JA Salzburg hoch, um die Einteilungen zu erfassen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onOpenImport) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Dienstplan HTML importieren")
            }
        }
    }
}

// --- Color & Label Helpers for Salzburg Shift Types ---

private fun getShiftBadgeColor(code: String): Color {
    val upper = code.uppercase()
    return when {
        upper == "D" -> Color(0xFF1976D2)       // Tagdienst (Primary Blue)
        upper == "ND" -> Color(0xFF1A237E)      // Nachtdienst (Dark Indigo/Navy)
        upper == "NF" -> Color(0xFF00897B)      // Nachtfolge (Teal)
        upper == "DB" || upper == "VD" -> Color(0xFF6A1B9A) // 11h/10h Dienst (Purple)
        upper == "TD" -> Color(0xFF8E24AA)      // Teildienst 5h (Violet)
        upper == "DR" -> Color(0xFF0288D1)      // Bereitschaft/Reserve (Light Blue)
        upper == "U" -> Color(0xFF2E7D32)       // Urlaub (Forest Green)
        upper == "FZA" -> Color(0xFFE65100)     // FZA Freizeitausgleich (Deep Orange)
        upper == "KR" || upper == "K" -> Color(0xFFC62828) // Krankenstand (Red)
        upper == "--W" -> Color(0xFF757575)     // Wochenende frei (Grey)
        else -> Color(0xFF455A64)
    }
}

private fun getShiftBgColor(code: String): Color {
    val upper = code.uppercase()
    return when {
        upper == "D" -> Color(0xFFE3F2FD)
        upper == "ND" -> Color(0xFFEDE7F6)
        upper == "NF" -> Color(0xFFE0F2F1)
        upper == "DB" || upper == "VD" -> Color(0xFFF3E5F5)
        upper == "TD" -> Color(0xFFF3E5F5)
        upper == "U" -> Color(0xFFE8F5E9)
        upper == "FZA" -> Color(0xFFFFF3E0)
        upper == "KR" || upper == "K" -> Color(0xFFFFEBEE)
        upper == "--W" -> Color(0xFFF5F5F5)
        else -> Color(0xFFECEFF1)
    }
}

private fun getShiftBorderColor(code: String): Color {
    val upper = code.uppercase()
    return when {
        upper == "D" -> Color(0xFF90CAF9)
        upper == "ND" -> Color(0xFFB39DDB)
        upper == "NF" -> Color(0xFF80CBC4)
        upper == "U" -> Color(0xFFA5D6A7)
        upper == "FZA" -> Color(0xFFFFCC80)
        else -> Color(0xFFB0BEC5)
    }
}

private fun getShiftDescription(code: String, dateStr: String): String {
    val upper = code.uppercase()
    val timing = JaSalzburgRules.getTimingForShift(code, dateStr)
    return when (upper) {
        "D" -> "Tagdienst (${formatShortHours(timing.activeHours)}h)"
        "ND" -> "Nachtdienst 24h (15h aktiv + 2h Journal)"
        "NF" -> "Nachtfolgezeit (1h aktiv + 6h Journal)"
        "DB" -> "Verlängerter Tagdienst DB (11h)"
        "VD" -> "Volldienst VD (10h)"
        "TD" -> "Teildienst TD (5h)"
        "DR" -> "Reservedienst DR"
        "U" -> "Urlaub (6-Tage-Woche: 06:40)"
        "FZA" -> "Freizeitausgleich FZA"
        "KR", "K" -> "Krankenstand"
        "--W" -> "Wochenende frei"
        else -> "Dienst $code"
    }
}

private fun formatShortHours(hours: Double): String {
    return if (hours == hours.toLong().toDouble()) {
        hours.toLong().toString()
    } else {
        String.format(Locale.GERMANY, "%.1f", hours)
    }
}

private fun formatWeekday(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
        val date = sdf.parse(dateStr) ?: return ""
        SimpleDateFormat("EEE", Locale.GERMANY).format(date)
    } catch (_: Exception) {
        ""
    }
}

private fun formatDisplayDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
        val date = sdf.parse(dateStr) ?: return dateStr
        SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(date)
    } catch (_: Exception) {
        dateStr
    }
}
