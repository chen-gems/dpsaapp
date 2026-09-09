package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ActualShiftEntity
import com.example.parser.HtmlScheduleParser
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftEditDialog(
    initialShift: ActualShiftEntity?,
    defaultDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date()),
    onDismiss: () -> Unit,
    onSave: (ActualShiftEntity) -> Unit
) {
    var date by remember { mutableStateOf(initialShift?.date ?: defaultDate) }
    var shiftCode by remember { mutableStateOf(initialShift?.shiftCode ?: "D") }
    var startTime by remember { mutableStateOf(initialShift?.startTime ?: "07:00") }
    var endTime by remember { mutableStateOf(initialShift?.endTime ?: "15:00") }
    var breakMinutes by remember { mutableIntStateOf(initialShift?.breakMinutes ?: 0) } // JA Salzburg: keine Pause
    var overtimeHours by remember { mutableDoubleStateOf(initialShift?.overtimeHours ?: 0.0) }
    var journalHours by remember { mutableDoubleStateOf(initialShift?.journalHours ?: 0.0) }
    var dutyOrLocation by remember { mutableStateOf(initialShift?.dutyOrLocation ?: "JA Salzburg") }
    var surcharges by remember { mutableStateOf(initialShift?.surcharges ?: "") }
    var notes by remember { mutableStateOf(initialShift?.notes ?: "") }

    // Calculated net hours
    val calculatedHours = remember(startTime, endTime, breakMinutes) {
        HtmlScheduleParser.calculateHoursFromTimes(startTime, endTime, breakMinutes)
    }

    fun applyShiftCode(code: String, selectedDate: String) {
        shiftCode = code
        val timing = com.example.model.JaSalzburgRules.getTimingForShift(code, selectedDate)
        startTime = timing.startTime
        endTime = timing.endTime
        breakMinutes = timing.breakMinutes
        journalHours = timing.journalHours
        if (timing.surcharges.isNotEmpty()) {
            surcharges = timing.surcharges
        }
        if (timing.isAbsence) {
            notes = timing.description
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.94f)
                    .testTag("shift_edit_dialog"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (initialShift == null) "Dienst erfassen (JA Salzburg)" else "Dienst bearbeiten",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Date Field
                        OutlinedTextField(
                            value = date,
                            onValueChange = {
                                date = it
                                if (shiftCode.isNotEmpty()) applyShiftCode(shiftCode, it)
                            },
                            label = { Text("Datum (JJJJ-MM-TT)") },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("shift_date_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Shift Code Quick Selector (Official JA Salzburg codes)
                        Column {
                            Text(
                                "Dienstkürzel (JA Salzburg - 6-Tage-Woche):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "D" to "Tag (8h/6h/5h)",
                                    "ND" to "24h (15h+2h Jn)",
                                    "NF" to "NF (1h+6h Jn)",
                                    "DB" to "11h (07-18)",
                                    "VD" to "10h (07-17)",
                                    "TD" to "5h (07-12)",
                                    "U" to "Url (06:40)"
                                ).forEach { (code, desc) ->
                                    FilterChip(
                                        selected = shiftCode == code,
                                        onClick = { applyShiftCode(code, date) },
                                        label = { Text(code, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                            Text(
                                text = when (shiftCode) {
                                    "D" -> "D = Tagdienst: Mo-Do 07-15 Uhr (8h), Fr 07-13 Uhr (6h), Sa/So/Ftg 07-12 Uhr (5h). Keine Pause."
                                    "ND" -> "ND = 24h-Dienst: 07-22 Uhr Dienst (15h/13h) + 2h Journalstunden (22-06 Uhr). Keine Pause."
                                    "NF" -> "NF = Nachtfolgetag: 06-07 Uhr (1h) + 6h Journalstunden (00-06 Uhr). Keine Pause."
                                    "DB" -> "DB = 11 Stunden von 07:00 bis 18:00 Uhr. Keine Pause."
                                    "VD" -> "VD = 10 Stunden von 07:00 bis 17:00 Uhr. Keine Pause."
                                    "TD" -> "TD = 5 Stunden von 07:00 bis 12:00 Uhr. Keine Pause."
                                    "U" -> "U = Urlaub: 06:40 Stunden (6-Tage-Wochenmodell)."
                                    else -> "Manuelle Eingabe der Dienstzeiten."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Times: Start & End
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = startTime,
                                onValueChange = { startTime = it },
                                label = { Text("Beginn (HH:mm)") },
                                modifier = Modifier.weight(1f).testTag("shift_start_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = endTime,
                                onValueChange = { endTime = it },
                                label = { Text("Ende (HH:mm)") },
                                modifier = Modifier.weight(1f).testTag("shift_end_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Calculated Hours Badge
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Berechnete Dienstzeit:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "Keine Pause abgezogen (JA Salzburg)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Text(
                                    "${String.format(Locale.GERMANY, "%.2f", calculatedHours)} Std.",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Journalstunden (22:00 - 06:00 Uhr)
                        Column {
                            Text("Journalstunden (22:00 - 06:00 Uhr):", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0.0, 2.0, 6.0, 8.0).forEach { jn ->
                                    FilterChip(
                                        selected = journalHours == jn,
                                        onClick = { journalHours = jn },
                                        label = { Text("${jn.toInt()}h Jn") }
                                    )
                                }
                            }
                        }

                        // Overtime selector
                        Column {
                            Text("Überstunden / Mehrarbeit:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0.0, 0.75, 1.0, 1.25, 1.75, 2.0).forEach { ot ->
                                    FilterChip(
                                        selected = overtimeHours == ot,
                                        onClick = { overtimeHours = ot },
                                        label = { Text("+${String.format(Locale.GERMANY, "%.2f", ot)}h") }
                                    )
                                }
                            }
                        }

                        // Location / Dienststelle
                        OutlinedTextField(
                            value = dutyOrLocation,
                            onValueChange = { dutyOrLocation = it },
                            label = { Text("Dienststelle") },
                            placeholder = { Text("JA Salzburg") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Surcharges (Zuschläge: SF-Zulage, Nacht, etc.)
                        OutlinedTextField(
                            value = surcharges,
                            onValueChange = { surcharges = it },
                            label = { Text("Zulagen & Vermerke") },
                            placeholder = { Text("z.B. SF-Zulage, Journaldienst, Nachtdienst") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Notes
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notizen / Begründung") },
                            placeholder = { Text("z.B. Längere Übergabe, Vertretung") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Abbrechen")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val finalShift = ActualShiftEntity(
                                    id = initialShift?.id ?: 0L,
                                    date = date,
                                    startTime = startTime,
                                    endTime = endTime,
                                    actualHours = calculatedHours,
                                    breakMinutes = breakMinutes,
                                    overtimeHours = overtimeHours,
                                    journalHours = journalHours,
                                    shiftCode = shiftCode,
                                    dutyOrLocation = dutyOrLocation,
                                    surcharges = surcharges,
                                    notes = notes,
                                    isConfirmed = true
                                )
                                onSave(finalShift)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("save_shift_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Speichern")
                        }
                    }
                }
            }
        }
    }
}
