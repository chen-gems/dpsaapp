package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ActualShiftEntity
import com.example.data.DpsaMonthSummaryEntity
import com.example.model.DayReconciliation
import com.example.model.MonthlyReconciliationSummary
import com.example.model.ReconciliationStatus
import com.example.ui.DiscrepancyFilter
import com.example.ui.ReconciliationUiState
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationScreen(
    uiState: ReconciliationUiState,
    onFilterChange: (DiscrepancyFilter) -> Unit,
    onOpenImport: (ImportTarget) -> Unit,
    onEditShift: (ActualShiftEntity?, String) -> Unit,
    onCopyPlanToActual: (DayReconciliation) -> Unit,
    onLoadDemoData: () -> Unit,
    onBackToOverview: (() -> Unit)? = null
) {
    var selectedReconciliation by remember { mutableStateOf<DayReconciliation?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reconciliation_screen")
    ) {
        // Back navigation to 12 months overview if opened from overview
        if (onBackToOverview != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBackToOverview,
                    modifier = Modifier.testTag("back_to_12m_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Zurück zur 12-Monate-Übersicht")
                }
            }
        }

        // KPI Dashboard Summary Card with DPSA Balances
        uiState.summary?.let { summary ->
            MonthlySummaryCard(
                summary = summary,
                dpsaSummary = uiState.dpsaSummary,
                onImportPlan = { onOpenImport(ImportTarget.PLANNED_SCHEDULE) },
                onImportBilling = { onOpenImport(ImportTarget.BILLING_STATEMENT) }
            )
        }

        // Filter Bar & Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.filterType == DiscrepancyFilter.ALL,
                onClick = { onFilterChange(DiscrepancyFilter.ALL) },
                label = { Text("Alle (${uiState.summary?.totalDaysWithData ?: 0})") },
                modifier = Modifier.testTag("filter_all_btn")
            )
            FilterChip(
                selected = uiState.filterType == DiscrepancyFilter.DISCREPANCIES_ONLY,
                onClick = { onFilterChange(DiscrepancyFilter.DISCREPANCIES_ONLY) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val count = (uiState.summary?.discrepancyCount ?: 0) + (uiState.summary?.missingBillingCount ?: 0)
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(StatusError, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Abweichungen ($count)")
                    }
                },
                modifier = Modifier.testTag("filter_discrepancies_btn")
            )
            FilterChip(
                selected = uiState.filterType == DiscrepancyFilter.OVERTIME_ONLY,
                onClick = { onFilterChange(DiscrepancyFilter.OVERTIME_ONLY) },
                label = { Text("Überstunden & Journal") },
                modifier = Modifier.testTag("filter_overtime_btn")
            )
        }

        // List of Reconciliations
        if (uiState.reconciliations.isEmpty()) {
            EmptyReconciliationState(
                onImportPlan = { onOpenImport(ImportTarget.PLANNED_SCHEDULE) },
                onImportBilling = { onOpenImport(ImportTarget.BILLING_STATEMENT) },
                onLoadDemo = onLoadDemoData
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
            ) {
                items(uiState.reconciliations, key = { it.date }) { item ->
                    DayReconciliationCard(
                        item = item,
                        onClick = { selectedReconciliation = item },
                        onQuickCopy = { onCopyPlanToActual(item) }
                    )
                }
            }
        }
    }

    // Detail Dialog
    selectedReconciliation?.let { item ->
        ReconciliationDetailDialog(
            reconciliation = item,
            onDismiss = { selectedReconciliation = null },
            onEditActual = { actual ->
                selectedReconciliation = null
                onEditShift(actual, item.date)
            },
            onCopyPlanToActual = {
                onCopyPlanToActual(item)
                selectedReconciliation = null
            }
        )
    }
}

@Composable
fun MonthlySummaryCard(
    summary: MonthlyReconciliationSummary,
    dpsaSummary: DpsaMonthSummaryEntity?,
    onImportPlan: () -> Unit,
    onImportBilling: () -> Unit
) {
    var expandedDpsa by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
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
                        text = if (dpsaSummary != null) "${dpsaSummary.dienststelle} • ${dpsaSummary.period}" else "Monatsübersicht & Abgleich",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (dpsaSummary != null && dpsaSummary.mitarbeiterName.isNotEmpty()) {
                        Text(
                            text = dpsaSummary.mitarbeiterName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Discrepancy indicator badge
                val totalIssues = summary.discrepancyCount + summary.missingBillingCount
                if (totalIssues == 0 && summary.totalDaysWithData > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Alles stimmig") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = StatusSuccessContainer)
                    )
                } else if (totalIssues > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("$totalIssues Abweichungen") },
                        leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = StatusErrorContainer)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Grid Metric Columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("Plan (Soll)", "${formatHours(summary.totalPlannedHours)} h", MaterialTheme.colorScheme.primary)
                MetricColumn("Ist (Geleistet)", "${formatHours(summary.totalActualHours)} h", MaterialTheme.colorScheme.secondary)
                MetricColumn("Überstunden", "+${formatHours(summary.totalActualOvertime)} h", AccentAmber)
                MetricColumn("Abrechnung", "${formatHours(summary.totalBilledHours)} h", MaterialTheme.colorScheme.tertiary)
            }

            // Difference Banner
            val netDiff = summary.netHoursDiscrepancy
            val netOtDiff = summary.netOvertimeDiscrepancy
            if (kotlin.math.abs(netDiff) > 0.05 || kotlin.math.abs(netOtDiff) > 0.05 || summary.missingBillingCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusErrorContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusError, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (netDiff > 0.05)
                                "Differenz: Es fehlen ${formatHours(netDiff)} Std. auf der Abrechnung!"
                            else if (summary.missingBillingCount > 0)
                                "${summary.missingBillingCount} Schicht(en) fehlen in der Abrechnung!"
                            else
                                "Überstunden-Saldo weicht um ${formatHours(netOtDiff)} Std. ab!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = OnStatusErrorContainer
                        )
                    }
                }
            }

            // DPSA Official Balances Expansion if available
            if (dpsaSummary != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedDpsa = !expandedDpsa },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DPSA Salden: FZA ${formatHours(dpsaSummary.folgemonatFza)}h • ZGS §82b ${formatHours(dpsaSummary.zgsOffenBer)}h • SF ${formatHours(dpsaSummary.sfZulage)}h",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        if (expandedDpsa) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Details",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = expandedDpsa) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Vormonat vs Folgemonat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Vormonat Übertrag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("PflichtStd: ${formatHours(dpsaSummary.vormonatPflichtStd)}h", style = MaterialTheme.typography.bodySmall)
                                Text("FZA: ${formatHours(dpsaSummary.vormonatFza)}h", style = MaterialTheme.typography.bodySmall)
                                Text("Urlaub: ${formatHours(dpsaSummary.vormonatUrlaub)}h", style = MaterialTheme.typography.bodySmall)
                            }
                            Column {
                                Text("Folgemonat Stand", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("PflichtStd: ${formatHours(dpsaSummary.folgemonatPflichtStd)}h", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("FZA: ${formatHours(dpsaSummary.folgemonatFza)}h", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Urlaub: ${formatHours(dpsaSummary.folgemonatUrlaub)}h", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Zulagen & Zeitgutschrift
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Zeitgutschrift §82b", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("ZGS neu: ${formatHours(dpsaSummary.zgsNeu)}h", style = MaterialTheme.typography.bodySmall)
                                Text("Offen (ber.): ${formatHours(dpsaSummary.zgsOffenBer)}h", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("Abgeltung: ${formatHours(dpsaSummary.zgsAbgeltung)}h", style = MaterialTheme.typography.bodySmall)
                            }
                            Column {
                                Text("Zulagen / Diverse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("SF-Zulage: ${formatHours(dpsaSummary.sfZulage)}h", style = MaterialTheme.typography.bodySmall)
                                Text("JD-Std: ${formatHours(dpsaSummary.journalDienstStd)}h", style = MaterialTheme.typography.bodySmall)
                                Text("Gefahren-Std: ${formatHours(dpsaSummary.gefahrenStd)}h", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun DayReconciliationCard(
    item: DayReconciliation,
    onClick: () -> Unit,
    onQuickCopy: () -> Unit
) {
    val weekday = formatWeekday(item.date)
    val formattedDay = formatDisplayDate(item.date)
    val isWeekendOrHoliday = weekday.startsWith("Sa") || weekday.startsWith("So")

    val statusInfo = when (item.status) {
        ReconciliationStatus.MATCH -> StatusDisplayInfo(
            MaterialTheme.colorScheme.surface,
            StatusSuccess.copy(alpha = 0.4f),
            Icons.Default.CheckCircle,
            "Stimmt"
        )
        ReconciliationStatus.DEVIATION_HOURS -> StatusDisplayInfo(
            StatusErrorContainer.copy(alpha = 0.25f),
            StatusError.copy(alpha = 0.6f),
            Icons.Default.Warning,
            "Stunden-Abweichung"
        )
        ReconciliationStatus.MISSING_IN_BILLING -> StatusDisplayInfo(
            StatusErrorContainer.copy(alpha = 0.35f),
            StatusError,
            Icons.Default.Error,
            "Fehlt in Abrechnung!"
        )
        ReconciliationStatus.OVERTIME_MISMATCH -> StatusDisplayInfo(
            StatusWarningContainer.copy(alpha = 0.35f),
            StatusWarning,
            Icons.Default.PriorityHigh,
            "Überstunden prüfen"
        )
        ReconciliationStatus.JOURNAL_MISMATCH -> StatusDisplayInfo(
            StatusWarningContainer.copy(alpha = 0.35f),
            StatusWarning,
            Icons.Default.Nightlight,
            "Journalstunden prüfen"
        )
        ReconciliationStatus.PLANNED_NOT_LOGGED -> StatusDisplayInfo(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.outlineVariant,
            Icons.Default.Info,
            "Noch nicht erfasst"
        )
        ReconciliationStatus.EXTRA_UNPLANNED_SHIFT -> StatusDisplayInfo(
            SecondaryTealContainer.copy(alpha = 0.25f),
            SecondaryTeal,
            Icons.Default.AddCircle,
            "Zusatzdienst"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("day_card_${item.date}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = statusInfo.bg),
        border = BorderStroke(1.dp, statusInfo.color)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Date & Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isWeekendOrHoliday) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = weekday,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isWeekendOrHoliday) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = formattedDay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.shiftCode.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = item.shiftCode,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            if (item.billedJournalHours > 0 || item.plannedJournalHours > 0) {
                                val jn = if (item.billedJournalHours > 0) item.billedJournalHours else item.plannedJournalHours
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${jn.toInt()}h Jn",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            if (item.absenceHours > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Abw: ${formatHours(item.absenceHours)}h",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Status Chip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when (item.status) {
                        ReconciliationStatus.MATCH -> StatusSuccessContainer
                        ReconciliationStatus.DEVIATION_HOURS, ReconciliationStatus.MISSING_IN_BILLING -> StatusErrorContainer
                        ReconciliationStatus.OVERTIME_MISMATCH, ReconciliationStatus.JOURNAL_MISMATCH -> StatusWarningContainer
                        ReconciliationStatus.EXTRA_UNPLANNED_SHIFT -> SecondaryTealContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            statusInfo.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when (item.status) {
                                ReconciliationStatus.MATCH -> StatusSuccess
                                ReconciliationStatus.DEVIATION_HOURS, ReconciliationStatus.MISSING_IN_BILLING -> StatusError
                                ReconciliationStatus.OVERTIME_MISMATCH, ReconciliationStatus.JOURNAL_MISMATCH -> StatusWarning
                                ReconciliationStatus.EXTRA_UNPLANNED_SHIFT -> SecondaryTeal
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusInfo.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3-Way Hour Comparison Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Plan
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Plan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (item.planned != null) "${formatHours(item.plannedHours)}h" else "-",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text("➔", color = MaterialTheme.colorScheme.outlineVariant)

                // Ist
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ist (Geleistet)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.actual != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${formatHours(item.actualHours)}h",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (item.actualOvertime > 0) {
                                Text(
                                    text = " (+${formatHours(item.actualOvertime)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusWarning,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text("-", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Text("➔", color = MaterialTheme.colorScheme.outlineVariant)

                // Abrechnung
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Abrechnung", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.billing != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${formatHours(item.billedHours)}h",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (kotlin.math.abs(item.hourDiffBilledVsActual) < 0.05) StatusSuccess else StatusError
                            )
                            if (item.billedOvertime > 0) {
                                Text(
                                    text = " (+${formatHours(item.billedOvertime)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text("FEHLT", fontWeight = FontWeight.Bold, color = StatusError, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Warnings / Discrepancy Note if present
            if (item.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item.issues.forEach { issue ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = issue,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Quick action if planned not yet logged
            if (item.status == ReconciliationStatus.PLANNED_NOT_LOGGED) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onQuickCopy,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Plan-Zeiten als Ist übernehmen", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyReconciliationState(
    onImportPlan: () -> Unit,
    onImportBilling: () -> Unit,
    onLoadDemo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Dienstdaten Justizanstalt Salzburg",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Importiere deinen DPSA Dienst-Streifen (HTML) und deine DPSA Stunden-Abrechnung (HTML), um Dienststunden, Überstunden und Journalstunden automatisch abzugleichen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onLoadDemo,
                    modifier = Modifier.fillMaxWidth().testTag("empty_load_demo_btn")
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reale DPSA-Daten (August 2026) laden")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportPlan, modifier = Modifier.weight(1f)) {
                        Text("Dienst-Streifen HTML")
                    }
                    OutlinedButton(onClick = onImportBilling, modifier = Modifier.weight(1f)) {
                        Text("Abrechnung HTML")
                    }
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
        SimpleDateFormat("dd. MMMM yyyy", Locale.GERMANY).format(date)
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatHours(hours: Double): String {
    return String.format(Locale.GERMANY, "%.2f", hours)
}

data class StatusDisplayInfo(
    val bg: Color,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
)
