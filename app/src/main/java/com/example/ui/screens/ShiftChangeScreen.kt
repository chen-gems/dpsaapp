package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActualShiftEntity
import com.example.data.PlannedShiftEntity
import com.example.model.JaSalzburgRules
import com.example.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Bereich 3: Änderung mit Auswahl von Tag.
 * Anpassung NUR durch Änderung der Kennzeichnung (z.B. statt D ist VD)
 * oder durch zusätzliche Stunden möglich.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShiftChangeScreen(
    selectedMonth: String,
    onSelectMonth: (String) -> Unit,
    plannedShifts: List<PlannedShiftEntity>,
    actualShifts: List<ActualShiftEntity>,
    onApplyShiftChange: (date: String, newShiftCode: String, additionalHours: Double) -> Unit,
    onDeleteActualShift: (Long) -> Unit
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

    // Days in current month (1..maxDay)
    val daysCount = remember(currentYm) { currentYm.lengthOfMonth() }

    // Currently selected day in month (default to 1 or today's day if in current month)
    var selectedDayNum by remember(activeMonth) { mutableIntStateOf(1) }

    val selectedDate = remember(activeMonth, selectedDayNum) {
        String.format(Locale.US, "%s-%02d", activeMonth, selectedDayNum)
    }

    val selectedLocalDate = remember(selectedDate) {
        try {
            LocalDate.parse(selectedDate)
        } catch (_: Exception) {
            LocalDate.now()
        }
    }

    // Lookup planned & actual for selected date
    val plannedForDate = remember(plannedShifts, selectedDate) {
        plannedShifts.firstOrNull { it.date == selectedDate }
    }

    val actualForDate = remember(actualShifts, selectedDate) {
        actualShifts.firstOrNull { it.date == selectedDate }
    }

    // Editable state:
    // Initialized from actual shift if exists, otherwise planned shift, otherwise "D"
    val defaultCode = remember(plannedForDate, actualForDate) {
        actualForDate?.shiftCode?.ifEmpty { null }
            ?: plannedForDate?.shiftCode?.ifEmpty { null }
            ?: "D"
    }

    val defaultAdditional = remember(actualForDate) {
        actualForDate?.overtimeHours ?: 0.0
    }

    var selectedCode by remember(selectedDate, defaultCode) { mutableStateOf(defaultCode) }
    var additionalHours by remember(selectedDate, defaultAdditional) { mutableDoubleStateOf(defaultAdditional) }
    var customHoursInput by remember(selectedDate, defaultAdditional) {
        mutableStateOf(if (defaultAdditional > 0) String.format(Locale.GERMANY, "%.2f", defaultAdditional) else "")
    }

    val isVdAllowedToday = remember(selectedDate) {
        JaSalzburgRules.isVDAllowed(selectedDate)
    }

    // Falls an Sa, So oder Feiertag VD ausgewählt wäre, auf D zurückfallen
    LaunchedEffect(selectedDate, isVdAllowedToday) {
        if (selectedCode == "VD" && !isVdAllowedToday) {
            selectedCode = if (defaultCode != "VD") defaultCode else "D"
        }
    }

    // Calculate timing from rules for selected date & selectedCode
    val timing = remember(selectedCode, selectedDate) {
        JaSalzburgRules.getTimingForShift(selectedCode, selectedDate)
    }

    val baseHours = timing.activeHours
    val totalEffectiveHours = baseHours + additionalHours

    // All available Salzburg codes for quick selection
    val availableCodes = listOf(
        "D" to "Tagdienst (Mo-Do 8h, Fr 6h, Sa/So 5h)",
        "VD" to "Verlängerter Dienst (Mo-Do 07-17 [10h], Fr 07-15 [8h])",
        "ND" to "Nachtdienst 24h (15h aktiv, 2h Journal)",
        "NF" to "Nachtfolge (1h aktiv, 6h Journal)",
        "DB" to "11h Dienst (07-18)",
        "TD" to "Teildienst (07-12, 5h)",
        "DR" to "Reservedienst (07-15, 8h)",
        "U" to "Urlaub (06:40 / 6,67h)",
        "FZA" to "Freizeitausgleich (6,67h)",
        "KR" to "Krankenstand (6,67h)",
        "--W" to "Frei (0h)"
    )

    // Month's changes summary (days that differ from plan or have extra hours)
    val plannedByDate = remember(plannedShifts, activeMonth) {
        plannedShifts.filter { it.date.startsWith(activeMonth) }.associateBy { it.date }
    }
    val actualByDate = remember(actualShifts, activeMonth) {
        actualShifts.filter { it.date.startsWith(activeMonth) }.associateBy { it.date }
    }

    val changedDaysInMonth = remember(plannedByDate, actualByDate, activeMonth) {
        (1..daysCount).mapNotNull { d ->
            val dt = String.format(Locale.US, "%s-%02d", activeMonth, d)
            val p = plannedByDate[dt]
            val a = actualByDate[dt]
            val hasChange = when {
                a != null && p != null -> a.shiftCode != p.shiftCode || Math.abs(a.actualHours - p.plannedHours) > 0.01 || a.overtimeHours > 0
                a != null && p == null -> true
                else -> false
            }
            if (hasChange && a != null) Pair(dt, a) else null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("shift_change_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Month Navigation Header ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val prev = currentYm.minusMonths(1).toString()
                            onSelectMonth(prev)
                        },
                        modifier = Modifier.testTag("shift_change_prev_month")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voriger Monat")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = monthDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Änderungen: ${changedDaysInMonth.size} im Monat",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val next = currentYm.plusMonths(1).toString()
                            onSelectMonth(next)
                        },
                        modifier = Modifier.testTag("shift_change_next_month")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Nächster Monat")
                    }
                }
            }
        }

        // --- 2. Day Selector Bar (Tag auswählen) ---
        item {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. Tag auswählen:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ausgewählt: $selectedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Horizontal scrollable strip of month days
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items((1..daysCount).toList()) { d ->
                        val dateStr = String.format(Locale.US, "%s-%02d", activeMonth, d)
                        val isSelected = d == selectedDayNum
                        val pShift = plannedByDate[dateStr]
                        val aShift = actualByDate[dateStr]
                        val isChanged = aShift != null && (pShift == null || aShift.shiftCode != pShift.shiftCode || aShift.overtimeHours > 0)
                        val dow = try {
                            LocalDate.parse(dateStr).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMANY)
                        } catch (_: Exception) {
                            ""
                        }
                        val isWeekend = dow.equals("Sa", ignoreCase = true) || dow.equals("So", ignoreCase = true)

                        val displayCode = aShift?.shiftCode?.ifEmpty { null }
                            ?: pShift?.shiftCode?.ifEmpty { null }
                            ?: "--"

                        Card(
                            modifier = Modifier
                                .width(56.dp)
                                .clickable { selectedDayNum = d }
                                .testTag("day_picker_$d"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else if (isChanged) {
                                    ShiftVDContainer.copy(alpha = 0.5f)
                                } else if (isWeekend) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else if (isChanged) {
                                    ShiftVD
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = dow,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = d.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                // Shift code badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (isChanged) {
                                        ShiftVD
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = displayCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected || isChanged) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Current Day Status Card (Vorher / Nachher) ---
        item {
            val dowLong = selectedLocalDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMANY)
            val dateFormatted = selectedLocalDate.format(DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale.GERMANY))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$dowLong, $dateFormatted",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Dienst anpassen (Kennzeichnung oder Zusatzstunden)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (actualForDate != null) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Geändert", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = ShiftVDContainer,
                                    labelColor = ShiftVD
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Plan Status Box
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Dienstplan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val pCode = plannedForDate?.shiftCode?.ifEmpty { "Frei" } ?: "Frei"
                                val pHours = plannedForDate?.plannedHours ?: 0.0
                                Text(
                                    text = "$pCode (${String.format(Locale.GERMANY, "%.2f", pHours)}h)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (plannedForDate != null && plannedForDate.startTime.isNotEmpty()) {
                                    Text(
                                        text = "${plannedForDate.startTime} - ${plannedForDate.endTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Actual Status Box
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (actualForDate != null) ShiftVDContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, if (actualForDate != null) ShiftVD else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Aktuell Erfasst",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val aCode = actualForDate?.shiftCode?.ifEmpty { "Nicht erfasst" } ?: "Wie Plan"
                                val aHours = actualForDate?.actualHours ?: plannedForDate?.plannedHours ?: 0.0
                                Text(
                                    text = "$aCode (${String.format(Locale.GERMANY, "%.2f", aHours)}h)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (actualForDate != null) ShiftVD else MaterialTheme.colorScheme.onSurface
                                )
                                if (actualForDate?.overtimeHours ?: 0.0 > 0.0) {
                                    Text(
                                        text = "+${String.format(Locale.GERMANY, "%.2f", actualForDate?.overtimeHours)}h Zusatz",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OvertimeColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Selection: Shift Code / Kennzeichnung (z.B. statt D ist VD) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "2. Dienst-Kennzeichnung auswählen:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "z.B. statt D ist VD (Zeiten werden automatisch berechnet)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // FlowRow of shift codes
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCodes.forEach { (code, desc) ->
                            val isSelected = selectedCode == code
                            val isPlanCode = plannedForDate?.shiftCode == code
                            val isVd = code == "VD"
                            val isEnabled = if (isVd) isVdAllowedToday else true

                            FilterChip(
                                selected = isSelected,
                                onClick = { if (isEnabled) selectedCode = code },
                                enabled = isEnabled,
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isVd && !isVdAllowedToday) "$code (kein VD)" else code,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        if (isPlanCode) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "(Plan)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.testTag("code_chip_$code")
                            )
                        }
                    }

                    if (!isVdAllowedToday) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Hinweis: An Sa, So und Feiertagen gibt es keinen Verlängerten Dienst (VD).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Selected code rule detail info
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = timing.description.ifEmpty { "Dienst $selectedCode" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Arbeitszeit: ${timing.startTime} - ${timing.endTime} • Basis: ${String.format(Locale.GERMANY, "%.2f", timing.activeHours)}h" +
                                            if (timing.journalHours > 0) " + ${String.format(Locale.GERMANY, "%.1f", timing.journalHours)}h Journal" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 5. Selection: Additional Hours (Zusätzliche Stunden) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "3. Zusätzliche Stunden (optional):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mehrleistung oder Überstunden auf die Basis-Dienstzeit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick buttons for additional hours
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.0, 0.5, 1.0, 1.5, 2.0, 3.0).forEach { h ->
                            val isSelected = Math.abs(additionalHours - h) < 0.01
                            FilledTonalButton(
                                onClick = {
                                    additionalHours = h
                                    customHoursInput = if (h > 0) String.format(Locale.GERMANY, "%.2f", h) else ""
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = if (h == 0.0) "0h" else "+${if (h % 1.0 == 0.0) h.toInt().toString() else h.toString()}h",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stepper / Custom input row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val newVal = maxOf(0.0, additionalHours - 0.5)
                                additionalHours = newVal
                                customHoursInput = if (newVal > 0) String.format(Locale.GERMANY, "%.2f", newVal) else ""
                            },
                            enabled = additionalHours > 0.0
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Minus 0.5h")
                        }

                        OutlinedTextField(
                            value = customHoursInput,
                            onValueChange = { input ->
                                customHoursInput = input
                                val parsed = input.replace(",", ".").toDoubleOrNull()
                                if (parsed != null && parsed >= 0.0) {
                                    additionalHours = parsed
                                } else if (input.isEmpty()) {
                                    additionalHours = 0.0
                                }
                            },
                            label = { Text("Zusatzstunden") },
                            placeholder = { Text("0,00") },
                            suffix = { Text("h") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("additional_hours_input")
                        )

                        IconButton(
                            onClick = {
                                val newVal = additionalHours + 0.5
                                additionalHours = newVal
                                customHoursInput = String.format(Locale.GERMANY, "%.2f", newVal)
                            }
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Plus 0.5h")
                        }
                    }
                }
            }
        }

        // --- 6. Calculated Result & Save Button ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ergebnis der Änderung:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Dienstcode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(selectedCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Basis + Zusatz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${String.format(Locale.GERMANY, "%.2f", baseHours)}h + ${String.format(Locale.GERMANY, "%.2f", additionalHours)}h",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Gesamt Ist", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${String.format(Locale.GERMANY, "%.2f", totalEffectiveHours)} h",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    val plannedHours = plannedForDate?.plannedHours ?: 0.0
                    val diffToPlan = totalEffectiveHours - plannedHours
                    if (Math.abs(diffToPlan) > 0.01) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Differenz zum Plan (${String.format(Locale.GERMANY, "%.2f", plannedHours)}h): " +
                                    "${if (diffToPlan > 0) "+" else ""}${String.format(Locale.GERMANY, "%.2f", diffToPlan)} h",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (diffToPlan > 0) OvertimeColor else MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (actualForDate != null) {
                            OutlinedButton(
                                onClick = { onDeleteActualShift(actualForDate.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reset_shift_btn"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auf Plan zurücksetzen", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                onApplyShiftChange(selectedDate, selectedCode, additionalHours)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_shift_change_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Änderung speichern")
                        }
                    }
                }
            }
        }

        // --- 7. List of Changed Days in this Month ---
        if (changedDaysInMonth.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Bereits geänderte Tage im $monthDisplayName (${changedDaysInMonth.size}):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Klicken, um den Tag direkt zu laden und weiter zu bearbeiten",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        changedDaysInMonth.forEach { (dateStr, actual) ->
                            val plan = plannedByDate[dateStr]
                            val pCode = plan?.shiftCode ?: "--"
                            val pHours = plan?.plannedHours ?: 0.0
                            val dayNum = dateStr.takeLast(2).toIntOrNull() ?: 1

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedDayNum = dayNum },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedDayNum == dayNum) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, if (selectedDayNum == dayNum) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Plan: $pCode (${String.format(Locale.GERMANY, "%.2f", pHours)}h) ➔ Änd: ${actual.shiftCode} (${String.format(Locale.GERMANY, "%.2f", actual.actualHours)}h)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (actual.overtimeHours > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = OvertimeContainer
                                        ) {
                                            Text(
                                                text = "+${String.format(Locale.GERMANY, "%.1f", actual.overtimeHours)}h",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OvertimeColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
