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
import androidx.compose.ui.unit.sp
import com.example.model.MonthOverviewItem
import com.example.model.SixMonthsSummary
import com.example.ui.ReconciliationUiState
import com.example.ui.theme.*
import java.util.Locale

/**
 * Bereich 1: Übersicht über die letzten 6 Monate mit Soll- und Ist-Vergleich
 * nach Abrechnung und Änderungen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SixMonthsOverviewScreen(
    uiState: ReconciliationUiState,
    onSelectMonth: (String) -> Unit,
    onOpenMonthDetail: (String) -> Unit,
    onOpenImportPlan: () -> Unit,
    onOpenImportBilling: () -> Unit
) {
    val summary = uiState.sixMonthsSummary ?: uiState.twelveMonthsSummary
    val monthsList = uiState.sixMonthsList.ifEmpty { uiState.twelveMonthsList }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("six_months_overview_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. Haupt-Zusammenfassung der letzten 6 Monate ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("six_months_kpi_card"),
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
                                text = "Übersicht der letzten 6 Monate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val rangeText = if (monthsList.isNotEmpty()) {
                                "${monthsList.last().displayName} – ${monthsList.first().displayName}"
                            } else {
                                "JA Salzburg • 6-Monats-Vergleich"
                            }
                            Text(
                                text = rangeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Summary Status Badge
                        val totalDiscrepancies = summary?.totalDiscrepanciesCount ?: 0
                        val monthsCount = summary?.monthsWithDataCount ?: 0
                        if (totalDiscrepancies == 0 && monthsCount > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Abrechnung stimmig", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(14.dp))
                                },
                                colors = AssistChipDefaults.assistChipColors(containerColor = StatusSuccessContainer)
                            )
                        } else if (totalDiscrepancies > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("$totalDiscrepancies Abweichungen", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(14.dp))
                                },
                                colors = AssistChipDefaults.assistChipColors(containerColor = StatusErrorContainer)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Soll vs. Ist vs. Abrechnung
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Soll (Plan)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${formatHours(summary?.totalPlannedHours ?: 0.0)} h",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ist (inkl. Änd.)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${formatHours(summary?.totalActualHours ?: 0.0)} h",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Abrechnung", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${formatHours(summary?.totalBilledHours ?: 0.0)} h",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Row: Änderungen und Abrechnungs-Differenz
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Änderungen (Ist minus Soll)
                        val diffPlanActual = summary?.totalDiffPlannedVsActual ?: 0.0
                        val totalChanges = summary?.totalChangesCount ?: 0
                        Column {
                            Text("Änderungen (Ist - Soll)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${if (diffPlanActual > 0) "+" else ""}${formatHours(diffPlanActual)} h",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diffPlanActual > 0) OvertimeColor else MaterialTheme.colorScheme.onSurface
                                )
                                if (totalChanges > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "($totalChanges Dienste)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Abrechnungsvergleich (Ist minus Abrechnung)
                        val netDiscrepancy = summary?.netDiscrepancyHours ?: 0.0
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Nach Abrechnung", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (kotlin.math.abs(netDiscrepancy) < 0.01) "0,00 h (Vollstimmig)" else "${if (netDiscrepancy > 0) "+" else ""}${formatHours(netDiscrepancy)} h Abweichung",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (kotlin.math.abs(netDiscrepancy) < 0.01) StatusSuccess else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Salden Banner (FZA, ZGS §82b GehG, SF)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                    text = "DPSA-Salden:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                summary?.currentFzaBalance?.let { fza ->
                                    Text(
                                        text = "FZA: ${formatHours(fza)}h",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                summary?.currentZgsBalance?.let { zgs ->
                                    Text(
                                        text = "ZGS §82b: ${formatHours(zgs)}h",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                summary?.currentSfZulage?.let { sf ->
                                    Text(
                                        text = "SF: ${formatHours(sf)}h",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Section Header: Die 6 Monate im Einzelvergleich ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monate im Soll/Ist-Vergleich (${monthsList.size} Monate)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Klick für Tag-Abgleich",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- 3. Die 6 Monats-Karten ---
        items(monthsList, key = { it.period }) { item ->
            SixMonthCard(
                item = item,
                onClick = {
                    onSelectMonth(item.period)
                    onOpenMonthDetail(item.period)
                }
            )
        }
    }
}

@Composable
private fun SixMonthCard(
    item: MonthOverviewItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("six_month_card_${item.period}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCurrentMonth) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
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
            // Header: Month name & badge
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
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Aktiv",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                if (!item.hasData) {
                    SuggestionChip(
                        onClick = onClick,
                        label = { Text("Keine Daten", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                } else {
                    val issues = item.discrepancyCount + item.missingBillingCount
                    if (issues == 0) {
                        AssistChip(
                            onClick = onClick,
                            label = { Text("Stimmig", color = StatusSuccess, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(14.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = StatusSuccessContainer),
                            modifier = Modifier.height(28.dp)
                        )
                    } else {
                        AssistChip(
                            onClick = onClick,
                            label = { Text("$issues Abweichungen", color = StatusError, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(14.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = StatusErrorContainer),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (item.hasData) {
                // Comparison: Soll, Ist, Abrechnung
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Soll (Plan)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${formatHours(item.plannedHours)} h",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ist (Erfasst)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${formatHours(item.actualHours)} h",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Abrechnung", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${formatHours(item.billedHours)} h",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Breakdown: Änderungen & Nach Abrechnung
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Changes info
                    val diffPlan = item.diffPlannedVsActual
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.EditCalendar,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (item.changesCount > 0) ShiftVD else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.changesCount > 0) "${item.changesCount} Änderungen (${if (diffPlan > 0) "+" else ""}${formatHours(diffPlan)}h)" else "Keine Änderungen",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.changesCount > 0) ShiftVD else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (item.changesCount > 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    // Billing diff
                    val diffBilling = item.diffActualVsBilled
                    Text(
                        text = if (kotlin.math.abs(diffBilling) < 0.01) "Abrechnung: 0,00h Diff" else "Diff: ${if (diffBilling > 0) "+" else ""}${formatHours(diffBilling)}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (kotlin.math.abs(diffBilling) < 0.01) StatusSuccess else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Salden if present
                if (item.fzaBalance != null || item.zgsBalance != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        item.fzaBalance?.let {
                            Text(
                                text = "FZA: ${formatHours(it)}h  ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        item.zgsBalance?.let {
                            Text(
                                text = "ZGS: ${formatHours(it)}h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Für diesen Monat liegen noch keine Daten vor. Dienstplan oder Abrechnung per HTML importieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatHours(hours: Double): String {
    return String.format(Locale.GERMANY, "%.2f", hours)
}
