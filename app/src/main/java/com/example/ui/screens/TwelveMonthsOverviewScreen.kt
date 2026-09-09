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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.MonthOverviewItem
import com.example.model.TwelveMonthsSummary
import com.example.ui.ReconciliationUiState
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwelveMonthsOverviewScreen(
    uiState: ReconciliationUiState,
    onSelectMonth: (String) -> Unit,
    onOpenMonthDetail: (String) -> Unit,
    onOpenImportPlan: () -> Unit,
    onOpenImportBilling: () -> Unit,
    onLoadDemoData: () -> Unit
) {
    val summary = uiState.twelveMonthsSummary
    val monthsList = uiState.twelveMonthsList

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("twelve_months_overview_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Header & Annual 12-Month KPI Summary Card ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("twelve_months_kpi_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Übersicht der letzten 12 Monate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val rangeText = if (monthsList.isNotEmpty()) {
                                "${monthsList.last().displayName} – ${monthsList.first().displayName}"
                            } else {
                                "JA Salzburg • Dienst- und Abrechnungsübersicht"
                            }
                            Text(
                                text = rangeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Summary status badge
                        val totalDiscrepancies = summary?.totalDiscrepanciesCount ?: 0
                        val monthsCount = summary?.monthsWithDataCount ?: 0
                        if (totalDiscrepancies == 0 && monthsCount > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Alles stimmig") },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
                                },
                                colors = AssistChipDefaults.assistChipColors(containerColor = StatusSuccessContainer)
                            )
                        } else if (totalDiscrepancies > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("$totalDiscrepancies Abweichungen") },
                                leadingIcon = {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError)
                                },
                                colors = AssistChipDefaults.assistChipColors(containerColor = StatusErrorContainer)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4 Main KPIs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        KpiMetricColumn(
                            title = "Gesamt Ist",
                            value = "${formatHours(summary?.totalActualHours ?: 0.0)} h",
                            color = MaterialTheme.colorScheme.primary
                        )
                        KpiMetricColumn(
                            title = "Gesamt Soll",
                            value = "${formatHours(summary?.totalPlannedHours ?: 0.0)} h",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        KpiMetricColumn(
                            title = "Überstunden",
                            value = "+${formatHours(summary?.totalActualOvertime ?: 0.0)} h",
                            color = AccentAmber
                        )
                        KpiMetricColumn(
                            title = "Journaldienst",
                            value = "${formatHours(summary?.totalActualJournal ?: 0.0)} h",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Salden-Zeile: FZA, ZGS §82b GehG, SF-Zulage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Aktuelle DPSA-Salden:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            summary?.currentFzaBalance?.let { fza ->
                                Text(
                                    text = "FZA: ${formatHours(fza)}h",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            summary?.currentZgsBalance?.let { zgs ->
                                Text(
                                    text = "ZGS §82b: ${formatHours(zgs)}h",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            summary?.currentSfZulage?.let { sf ->
                                Text(
                                    text = "SF: ${formatHours(sf)}h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Net hours discrepancy banner if any
                    val netDiff = summary?.netDiscrepancyHours ?: 0.0
                    if (kotlin.math.abs(netDiff) > 0.05) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusErrorContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "12-Monate-Abrechnungsdifferenz: ${formatHours(netDiff)} Std. weichen ab!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnStatusErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monate im Einzelüberblick",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${summary?.monthsWithDataCount ?: 0} mit Daten",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // List of Month Cards
        items(monthsList, key = { it.period }) { item ->
            MonthCard(
                item = item,
                onSelectMonth = {
                    onSelectMonth(item.period)
                    onOpenMonthDetail(item.period)
                },
                onImportPlan = onOpenImportPlan,
                onImportBilling = onOpenImportBilling
            )
        }

        // Bottom Quick Import & Load Demo Actions
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Daten für weitere Monate erfassen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lade die DPSA-Dienststreifen oder Stunden-Abrechnungen per HTML-Import hoch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenImportPlan,
                            modifier = Modifier.weight(1f).testTag("import_plan_12m_btn")
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dienstplan", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onOpenImportBilling,
                            modifier = Modifier.weight(1f).testTag("import_billing_12m_btn")
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrechnung", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onLoadDemoData,
                            modifier = Modifier.weight(1f).testTag("load_demo_12m_btn")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Demo", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCard(
    item: MonthOverviewItem,
    onSelectMonth: () -> Unit,
    onImportPlan: () -> Unit,
    onImportBilling: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectMonth)
            .testTag("month_card_${item.period}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCurrentMonth) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (item.isCurrentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Month Name, Tag & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.isCurrentMonth) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "Aktiv",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Status Chip
                if (!item.hasData) {
                    SuggestionChip(
                        onClick = onSelectMonth,
                        label = { Text("Keine Daten", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                } else {
                    val totalIssues = item.discrepancyCount + item.missingBillingCount
                    if (totalIssues == 0) {
                        AssistChip(
                            onClick = onSelectMonth,
                            label = { Text("Stimmig", color = StatusSuccess) },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = StatusSuccessContainer),
                            modifier = Modifier.height(28.dp)
                        )
                    } else {
                        AssistChip(
                            onClick = onSelectMonth,
                            label = { Text("$totalIssues Abweichungen", color = StatusError) },
                            leadingIcon = {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = StatusErrorContainer),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (item.hasData) {
                // 4 Hours Metric Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MonthSubMetric("Plan", "${formatHours(item.plannedHours)}h")
                    MonthSubMetric("Ist", "${formatHours(item.actualHours)}h", isHighlighted = true)
                    MonthSubMetric("Überstd.", "+${formatHours(item.actualOvertime)}h", AccentAmber)
                    MonthSubMetric("Abrechnung", "${formatHours(item.billedHours)}h")
                }

                // Secondary row: Salden & Journal if present
                if (item.actualJournalHours > 0 || item.fzaBalance != null || item.zgsBalance != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.actualJournalHours > 0) {
                            Text(
                                text = "Journal: ${formatHours(item.actualJournalHours)}h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        item.fzaBalance?.let {
                            Text(
                                text = "FZA: ${formatHours(it)}h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        item.zgsBalance?.let {
                            Text(
                                text = "ZGS §82b: ${formatHours(it)}h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Noch keine Dienst- oder Abrechnungsdaten importiert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monatsabgleich öffnen",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun KpiMetricColumn(
    title: String,
    value: String,
    color: Color
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun MonthSubMetric(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isHighlighted: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}

private fun formatHours(hours: Double): String {
    return String.format(Locale.GERMANY, "%.2f", hours)
}
