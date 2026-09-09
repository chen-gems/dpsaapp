package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ActualShiftEntity
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActualHoursScreen(
    actualShifts: List<ActualShiftEntity>,
    selectedMonth: String,
    onAddNewShift: () -> Unit,
    onEditShift: (ActualShiftEntity) -> Unit,
    onDeleteShift: (Long) -> Unit
) {
    val monthFiltered = remember(actualShifts, selectedMonth) {
        if (selectedMonth.isNotEmpty()) {
            actualShifts.filter { it.date.startsWith(selectedMonth) }
        } else {
            actualShifts
        }
    }

    val totalActualHours = remember(monthFiltered) {
        monthFiltered.sumOf { it.actualHours }
    }

    val totalOvertime = remember(monthFiltered) {
        monthFiltered.sumOf { it.overtimeHours }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddNewShift,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Dienst eintragen") },
                modifier = Modifier.testTag("add_actual_shift_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("actual_hours_screen")
        ) {
            // Month Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Meine geleisteten Stunden",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${monthFiltered.size} Dienste erfasst",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Gesamt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${String.format(Locale.GERMANY, "%.2f", totalActualHours)} h",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Überstunden", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "+${String.format(Locale.GERMANY, "%.2f", totalOvertime)} h",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmber
                            )
                        }
                    }
                }
            }

            if (monthFiltered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Noch keine Dienststunden für diesen Monat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tippe auf '+ Dienst eintragen' oder übernimm geplante Dienste aus deinem Dienstplan mit einem Klick.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onAddNewShift) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dienst manuell erfassen")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(monthFiltered, key = { it.id }) { shift ->
                        ActualShiftCard(
                            shift = shift,
                            onEdit = { onEditShift(shift) },
                            onDelete = { onDeleteShift(shift.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActualShiftCard(
    shift: ActualShiftEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val weekday = formatWeekday(shift.date)
    val displayDate = formatDisplayDate(shift.date)

    Card(
        modifier = Modifier.fillMaxWidth().testTag("actual_card_${shift.date}"),
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
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = weekday,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                shift.shiftCode.ifEmpty { "Dienst" },
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        if (shift.overtimeHours > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = StatusWarning.copy(alpha = 0.15f)) {
                                Text(
                                    "+${String.format(Locale.GERMANY, "%.1f", shift.overtimeHours)}h ÜS",
                                    color = StatusWarning,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        if (shift.journalHours > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    "+${String.format(Locale.GERMANY, "%.0f", shift.journalHours)}h Jn",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${shift.startTime} - ${shift.endTime} • ${String.format(Locale.GERMANY, "%.2f", shift.actualHours)} Std. (${shift.breakMinutes}m Pause)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val extraInfo = listOf(shift.dutyOrLocation, shift.surcharges, shift.notes).filter { it.isNotEmpty() }
                    if (extraInfo.isNotEmpty()) {
                        Text(
                            text = extraInfo.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Bearbeiten",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Löschen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
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
