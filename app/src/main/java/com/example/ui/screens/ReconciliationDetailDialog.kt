package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ActualShiftEntity
import com.example.model.DayReconciliation
import com.example.model.ReconciliationStatus
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun ReconciliationDetailDialog(
    reconciliation: DayReconciliation,
    onDismiss: () -> Unit,
    onEditActual: (ActualShiftEntity?) -> Unit,
    onCopyPlanToActual: () -> Unit
) {
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
                    .fillMaxHeight(0.92f)
                    .testTag("reconciliation_detail_dialog"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Top Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "3-Wege-Abgleich (JA Salzburg)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Dienst am ${reconciliation.date}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status Banner
                    ReconciliationStatusBanner(reconciliation)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable 3-Way Details
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card 1: Planned Schedule
                        ReconciliationPillarCard(
                            title = "1. Vorgeplanter Dienstplan",
                            subtitle = reconciliation.planned?.sourceFileName?.ifEmpty { "HTML-Dienstplan" } ?: "Kein Eintrag",
                            icon = Icons.Default.CalendarMonth,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            accentColor = MaterialTheme.colorScheme.primary
                        ) {
                            if (reconciliation.planned != null) {
                                val p = reconciliation.planned
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DataRow("Dienstkürzel", p.shiftCode.ifEmpty { "Dienst" })
                                    DataRow("Geplante Zeit", "${p.startTime} - ${p.endTime}")
                                    DataRow("Pause", "${p.breakMinutes} Min. (keine Pause)")
                                    DataRow("Soll-Dienstzeit", "${formatHours(p.plannedHours)} Std.", isBold = true)
                                    if (p.journalHours > 0) {
                                        DataRow("Journalstunden (22-06)", "${formatHours(p.journalHours)} Std.", valueColor = MaterialTheme.colorScheme.tertiary)
                                    }
                                    if (p.dutyOrLocation.isNotEmpty()) DataRow("Dienststelle", p.dutyOrLocation)
                                    if (p.notes.isNotEmpty()) DataRow("Hinweis", p.notes)
                                }
                            } else {
                                Text(
                                    "Für diesen Tag war kein Dienst im vorgeplanten Dienstplan eingetragen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Card 2: Actual Shift (Eigene Eingaben)
                        ReconciliationPillarCard(
                            title = "2. Meine Erfassung (Ist-Zeiten)",
                            subtitle = if (reconciliation.actual != null) "Erfasst durch Mitarbeiter" else "Noch nicht eingetragen",
                            icon = Icons.Default.AccessTimeFilled,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            accentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            if (reconciliation.actual != null) {
                                val a = reconciliation.actual
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DataRow("Dienstkürzel", a.shiftCode.ifEmpty { "D" })
                                    DataRow("Geleistete Zeit", "${a.startTime} - ${a.endTime}")
                                    DataRow("Pause", "${a.breakMinutes} Min.")
                                    DataRow("Geleistete Dienstzeit", "${formatHours(a.actualHours)} Std.", isBold = true)
                                    if (a.journalHours > 0) {
                                        DataRow("Journalstunden", "${formatHours(a.journalHours)} Std.", valueColor = MaterialTheme.colorScheme.tertiary)
                                    }
                                    if (a.overtimeHours > 0) {
                                        DataRow(
                                            "Überstunden",
                                            "+${formatHours(a.overtimeHours)} Std.",
                                            valueColor = StatusWarning
                                        )
                                    }
                                    if (a.surcharges.isNotEmpty()) DataRow("Zuschläge / Vermerke", a.surcharges)
                                    if (a.dutyOrLocation.isNotEmpty()) DataRow("Dienststelle", a.dutyOrLocation)
                                    if (a.notes.isNotEmpty()) DataRow("Notiz", a.notes)
                                }
                            } else {
                                Column {
                                    Text(
                                        "Noch keine eigenen Ist-Zeiten für diesen Tag erfasst.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (reconciliation.planned != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = onCopyPlanToActual,
                                            modifier = Modifier.fillMaxWidth().testTag("copy_plan_to_actual_detail_btn")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Aus Plan in Ist-Zeiten übernehmen")
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: Billing Statement (Abrechnung aus DPSA HTML)
                        ReconciliationPillarCard(
                            title = "3. Abrechnung (DPSA Stunden-Abrechnung)",
                            subtitle = reconciliation.billing?.sourceFileName?.ifEmpty { "Abrechnung.html" } ?: "Fehlt in Abrechnung!",
                            icon = Icons.Default.ReceiptLong,
                            containerColor = if (reconciliation.billing != null)
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                            else
                                StatusErrorContainer.copy(alpha = 0.3f),
                            accentColor = if (reconciliation.billing != null) MaterialTheme.colorScheme.tertiary else StatusError
                        ) {
                            if (reconciliation.billing != null) {
                                val b = reconciliation.billing
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DataRow("Dienst / Art", b.shiftCode.ifEmpty { "Schicht" })
                                    if (b.billedStartTime.isNotEmpty()) {
                                        DataRow("Abrechnungs-Zeitraum", "${b.billedStartTime} - ${b.billedEndTime}")
                                    }
                                    DataRow("Abgerechnete Dienstzeit", "${formatHours(b.billedHours)} Std.", isBold = true)
                                    if (b.journalHours > 0) {
                                        DataRow("Journaldienst-Stunden", "${formatHours(b.journalHours)} Std.", valueColor = MaterialTheme.colorScheme.tertiary)
                                    }
                                    if (b.absenceHours > 0) {
                                        DataRow("Abwesenheitszeit (z.B. Urlaub)", "${formatHours(b.absenceHours)} Std.")
                                    }
                                    DataRow("Abgerechnete Überstunden", "${formatHours(b.billedOvertimeHours)} Std.", isBold = b.billedOvertimeHours > 0)
                                    if (b.billedSurcharges.isNotEmpty()) DataRow("Zulagen (SF, JD, etc.)", b.billedSurcharges)
                                    if (b.billedAmountEur != null) {
                                        DataRow("Betrag", "${String.format(Locale.GERMANY, "%.2f", b.billedAmountEur)} €")
                                    }
                                    if (b.notes.isNotEmpty()) DataRow("Abrechnungshinweis", b.notes)
                                }
                            } else {
                                Text(
                                    "FEHLANZEIGE: Dieser Tag ist in der Abrechnungs-HTML nicht enthalten!",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StatusError
                                )
                            }
                        }

                        // Summary comparison math box
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Differenz-Kalkulation:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                val hourDiff = reconciliation.hourDiffBilledVsActual
                                val otDiff = reconciliation.overtimeDiffBilledVsActual
                                val jnDiff = reconciliation.journalDiffBilledVsActual

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Abrechnung vs. Ist-Stunden:")
                                    Text(
                                        text = "${if (hourDiff >= 0) "+" else ""}${formatHours(hourDiff)} Std.",
                                        fontWeight = FontWeight.Bold,
                                        color = if (kotlin.math.abs(hourDiff) < 0.05) StatusSuccess else StatusError
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Abrechnung vs. Ist-Überstunden:")
                                    Text(
                                        text = "${if (otDiff >= 0) "+" else ""}${formatHours(otDiff)} Std.",
                                        fontWeight = FontWeight.Bold,
                                        color = if (kotlin.math.abs(otDiff) < 0.05) StatusSuccess else StatusWarning
                                    )
                                }
                                if (reconciliation.billedJournalHours > 0 || reconciliation.actualJournalHours > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Abrechnung vs. Ist-Journalstunden:")
                                        Text(
                                            text = "${if (jnDiff >= 0) "+" else ""}${formatHours(jnDiff)} Std.",
                                            fontWeight = FontWeight.Bold,
                                            color = if (kotlin.math.abs(jnDiff) < 0.05) StatusSuccess else StatusWarning
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dialog Bottom Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onEditActual(reconciliation.actual) },
                            modifier = Modifier.weight(1f).testTag("edit_actual_shift_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (reconciliation.actual == null) "Ist erfassen" else "Ist anpassen")
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Schließen")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReconciliationStatusBanner(reconciliation: DayReconciliation) {
    val bannerInfo = when (reconciliation.status) {
        ReconciliationStatus.MATCH -> BannerVisualInfo(
            StatusSuccessContainer,
            OnStatusSuccessContainer,
            Icons.Default.CheckCircle,
            "Vollständig stimmig: Plan, Ist & Abrechnung stimmen überein."
        )
        ReconciliationStatus.DEVIATION_HOURS -> BannerVisualInfo(
            StatusErrorContainer,
            OnStatusErrorContainer,
            Icons.Default.Warning,
            "Stunden-Abweichung: Abrechnung stimmt nicht mit geleisteter Zeit überein!"
        )
        ReconciliationStatus.MISSING_IN_BILLING -> BannerVisualInfo(
            StatusErrorContainer,
            OnStatusErrorContainer,
            Icons.Default.Error,
            "Dienst fehlt komplett in der Abrechnung! (Prüfen & Reklamieren)"
        )
        ReconciliationStatus.OVERTIME_MISMATCH -> BannerVisualInfo(
            StatusWarningContainer,
            OnStatusWarningContainer,
            Icons.Default.PriorityHigh,
            "Überstunden-Abweichung: Nicht alle Mehrarbeitsstunden abgerechnet!"
        )
        ReconciliationStatus.JOURNAL_MISMATCH -> BannerVisualInfo(
            StatusWarningContainer,
            OnStatusWarningContainer,
            Icons.Default.Nightlight,
            "Journalstunden-Abweichung: Journalstunden (22-06 Uhr) weichen ab!"
        )
        ReconciliationStatus.PLANNED_NOT_LOGGED -> BannerVisualInfo(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.Info,
            "Vorgeplant: Noch keine eigene Arbeitszeit eingetragen."
        )
        ReconciliationStatus.EXTRA_UNPLANNED_SHIFT -> BannerVisualInfo(
            SecondaryTealContainer,
            OnSecondaryTealContainer,
            Icons.Default.AddCircle,
            "Zusatzschicht: Nicht im Dienstplan, aber erfasst und abgerechnet."
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bannerInfo.bg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(bannerInfo.icon, contentDescription = null, tint = bannerInfo.textColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = bannerInfo.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = bannerInfo.textColor
            )
        }
    }
}

@Composable
private fun ReconciliationPillarCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accentColor)
                }
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DataRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatHours(hours: Double): String {
    return String.format(Locale.GERMANY, "%.2f", hours)
}

private data class BannerVisualInfo(
    val bg: Color,
    val textColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val text: String
)
