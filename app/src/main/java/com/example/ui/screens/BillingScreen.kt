package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.data.BillingShiftEntity
import com.example.data.DpsaMonthSummaryEntity
import com.example.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Bereich 4: Abrechnung in Monatsansicht nur mit Funktion HTML Import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    billingShifts: List<BillingShiftEntity>,
    dpsaSummary: DpsaMonthSummaryEntity? = null,
    selectedMonth: String,
    onSelectMonth: (String) -> Unit = {},
    onOpenImport: () -> Unit,
    onDeleteShift: (Long) -> Unit = {}
) {
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

    val monthFiltered = remember(billingShifts, activeMonth) {
        billingShifts.filter { it.date.startsWith(activeMonth) || it.billingPeriod == activeMonth }
    }

    val billingByDate = remember(monthFiltered) {
        monthFiltered.associateBy { it.date }
    }

    val totalBilledHours = remember(monthFiltered) {
        monthFiltered.sumOf { it.billedHours }
    }

    val totalBilledOvertime = remember(monthFiltered) {
        monthFiltered.sumOf { it.billedOvertimeHours }
    }

    val totalJournalHours = remember(monthFiltered) {
        monthFiltered.sumOf { it.journalHours }
    }

    var selectedDayItem by remember { mutableStateOf<BillingShiftEntity?>(null) }
    var selectedEmptyDate by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("billing_screen")
    ) {
        // --- 1. Top Card: Month Navigation & HTML Import Only ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
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
                        modifier = Modifier.testTag("billing_prev_month_btn")
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
                            text = "${monthFiltered.size} abgerechnete Tage • ${String.format(Locale.GERMANY, "%.2f", totalBilledHours)} Std. abgerechnet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val next = currentYm.plusMonths(1).toString()
                            onSelectMonth(next)
                        },
                        modifier = Modifier.testTag("billing_next_month_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Nächster Monat")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // KPI Mini Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Abgerechnet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${String.format(Locale.GERMANY, "%.2f", totalBilledHours)} h",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Überstunden", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "+${String.format(Locale.GERMANY, "%.2f", totalBilledOvertime)} h",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OvertimeColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Journalstunden", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${String.format(Locale.GERMANY, "%.2f", totalJournalHours)} h",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Salden if present
                if (dpsaSummary != null) {
                    val fza = dpsaSummary.folgemonatFza ?: dpsaSummary.vormonatFza
                    val zgs = dpsaSummary.zgsOffenBer ?: dpsaSummary.vormonatZgsBer
                    if (fza != null || zgs != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                fza?.let {
                                    Text("FZA-Stand: ${String.format(Locale.GERMANY, "%.2f", it)}h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                zgs?.let {
                                    Text("ZGS §82b: ${String.format(Locale.GERMANY, "%.2f", it)}h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Import Button (nur funktion html import)
                Button(
                    onClick = onOpenImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("import_billing_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abrechnung HTML importieren", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // --- 2. Main Content: Abrechnung Monatsansicht (Kalender) ---
        BillingMonthCalendarView(
            yearMonth = currentYm,
            billingByDate = billingByDate,
            onDayClick = { date, item ->
                if (item != null) {
                    selectedDayItem = item
                } else {
                    selectedEmptyDate = date
                }
            }
        )
    }

    // Detail Dialog for clicked billing day
    selectedDayItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedDayItem = null },
            title = {
                Text("${formatDisplayDate(item.date)} (${formatWeekday(item.date)})")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Abrechnungscode", style = MaterialTheme.typography.labelSmall)
                                Text(item.shiftCode.ifEmpty { "Dienst" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Abgerechnete Stunden", style = MaterialTheme.typography.labelSmall)
                                Text("${String.format(Locale.GERMANY, "%.2f", item.billedHours)} h", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }

                    if (item.billedStartTime.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Abrechnungszeiten:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${item.billedStartTime} – ${item.billedEndTime}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (item.billedOvertimeHours > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Überstunden (UeStd):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("+${String.format(Locale.GERMANY, "%.2f", item.billedOvertimeHours)} h", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = OvertimeColor)
                        }
                    }

                    if (item.journalHours > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Journalstunden (JnStd):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format(Locale.GERMANY, "%.2f", item.journalHours)} h", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (item.absenceHours > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Abwesenheitsstunden:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format(Locale.GERMANY, "%.2f", item.absenceHours)} h", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (item.billedSurcharges.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Zuschläge:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.billedSurcharges, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (item.notes.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Hinweis:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDayItem = null }) {
                    Text("Schließen")
                }
            }
        )
    }

    selectedEmptyDate?.let { date ->
        AlertDialog(
            onDismissRequest = { selectedEmptyDate = null },
            title = { Text("Keine Abrechnung erfasst") },
            text = { Text("Für den ${formatDisplayDate(date)} (${formatWeekday(date)}) liegt keine Abrechnungsposition im DPSA-Abrechnungsnachweis vor.") },
            confirmButton = {
                TextButton(onClick = { selectedEmptyDate = null }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * 7-Spaltige Monatskalenderansicht der Abrechnung (Montag bis Sonntag).
 */
@Composable
private fun BillingMonthCalendarView(
    yearMonth: YearMonth,
    billingByDate: Map<String, BillingShiftEntity>,
    onDayClick: (String, BillingShiftEntity?) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfMonth = yearMonth.atDay(1)
    val startDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1 (Mo) .. 7 (So)
    val leadingEmptyDays = startDayOfWeek - 1

    val weekdays = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Weekday Header Row
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEachIndexed { index, name ->
                    val isWeekend = index >= 5
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isWeekend) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Calendar Days Grid
            val totalCells = leadingEmptyDays + daysInMonth
            val rows = (totalCells + 6) / 7

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = false,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Leading Empty Cells
                items((0 until leadingEmptyDays).toList()) {
                    Box(modifier = Modifier.aspectRatio(0.85f))
                }

                // Month Days
                items((1..daysInMonth).toList()) { day ->
                    val date = String.format(Locale.US, "%s-%02d", yearMonth.toString(), day)
                    val billingItem = billingByDate[date]
                    val dayOfWeek = yearMonth.atDay(day).dayOfWeek
                    val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

                    BillingCalendarDayCell(
                        day = day,
                        date = date,
                        item = billingItem,
                        isWeekend = isWeekend,
                        onClick = { onDayClick(date, billingItem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BillingCalendarDayCell(
    day: Int,
    date: String,
    item: BillingShiftEntity?,
    isWeekend: Boolean,
    onClick: () -> Unit
) {
    val hasBilling = item != null
    val isToday = date == LocalDate.now().toString()

    Surface(
        modifier = Modifier
            .aspectRatio(0.85f)
            .clickable(onClick = onClick)
            .testTag("billing_day_$date"),
        shape = RoundedCornerShape(8.dp),
        color = when {
            hasBilling -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            isWeekend -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isToday) 2.dp else 1.dp,
            color = when {
                isToday -> MaterialTheme.colorScheme.primary
                hasBilling -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Day Number
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday || hasBilling) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.primary
                    hasBilling -> MaterialTheme.colorScheme.tertiary
                    isWeekend -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontSize = 11.sp
            )

            if (item != null) {
                // Shift Code Pill
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        text = item.shiftCode.ifEmpty { "Abr" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp)
                    )
                }

                // Hours
                Text(
                    text = "${String.format(Locale.GERMANY, "%.1f", item.billedHours)}h",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}

private fun formatWeekday(dateStr: String): String {
    return try {
        val ld = LocalDate.parse(dateStr)
        ld.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMANY)
    } catch (_: Exception) {
        ""
    }
}

private fun formatDisplayDate(dateStr: String): String {
    return try {
        val ld = LocalDate.parse(dateStr)
        ld.format(DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale.GERMANY))
    } catch (_: Exception) {
        dateStr
    }
}
