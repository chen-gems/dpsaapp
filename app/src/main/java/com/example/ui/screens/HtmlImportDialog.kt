package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.parser.HtmlScheduleParser
import com.example.parser.SampleHtmlData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

enum class ImportTarget {
    PLANNED_SCHEDULE,
    BILLING_STATEMENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlImportDialog(
    initialTarget: ImportTarget,
    onDismiss: () -> Unit,
    onImportPlanned: (htmlContent: String, fileName: String) -> Unit,
    onImportBilling: (htmlContent: String, fileName: String) -> Unit
) {
    val context = LocalContext.current
    var selectedTarget by remember { mutableStateOf(initialTarget) }
    var htmlText by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("Import.html") }
    var detectedCount by remember { mutableIntStateOf(0) }
    var detectedPeriod by remember { mutableStateOf("") }
    var previewRows by remember { mutableStateOf<List<String>>(emptyList()) }

    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream))
                    val content = reader.readText()
                    htmlText = content
                    fileName = uri.lastPathSegment ?: "Import.html"
                }
            } catch (_: Exception) {
                // Ignore read errors
            }
        }
    }

    // Re-evaluate preview whenever text or target changes
    LaunchedEffect(htmlText, selectedTarget) {
        if (htmlText.isBlank()) {
            detectedCount = 0
            detectedPeriod = ""
            previewRows = emptyList()
        } else {
            try {
                val dpsa = HtmlScheduleParser.parseDpsaFull(htmlText, fileName)
                if (selectedTarget == ImportTarget.PLANNED_SCHEDULE) {
                    if (dpsa.plannedShifts.isNotEmpty()) {
                        detectedCount = dpsa.plannedShifts.size
                        detectedPeriod = dpsa.detectedPeriod
                        previewRows = dpsa.plannedShifts.take(4).map {
                            val jn = if (it.journalHours > 0) " (+${String.format(Locale.GERMANY, "%.0f", it.journalHours)}h Jn)" else ""
                            "${it.date}: ${it.shiftCode} ${it.startTime}-${it.endTime} (${String.format(Locale.GERMANY, "%.2f", it.plannedHours)} Std.)$jn"
                        }
                    } else {
                        val list = HtmlScheduleParser.parsePlannedSchedule(htmlText, fileName)
                        detectedCount = list.size
                        detectedPeriod = list.firstOrNull()?.date?.take(7) ?: ""
                        previewRows = list.take(4).map {
                            "${it.date}: ${it.shiftCode} ${it.startTime}-${it.endTime} (${String.format(Locale.GERMANY, "%.2f", it.plannedHours)} Std.)"
                        }
                    }
                } else {
                    if (dpsa.billingShifts.isNotEmpty()) {
                        detectedCount = dpsa.billingShifts.size
                        detectedPeriod = dpsa.detectedPeriod
                        previewRows = dpsa.billingShifts.take(4).map {
                            val ot = if (it.billedOvertimeHours > 0) ", ÜS: ${String.format(Locale.GERMANY, "%.2f", it.billedOvertimeHours)}h" else ""
                            val jn = if (it.journalHours > 0) ", Jn: ${String.format(Locale.GERMANY, "%.2f", it.journalHours)}h" else ""
                            "${it.date}: ${it.shiftCode} (${String.format(Locale.GERMANY, "%.2f", it.billedHours)} Std.$ot$jn)"
                        }
                    } else {
                        val list = HtmlScheduleParser.parseBillingStatement(htmlText, fileName)
                        detectedCount = list.size
                        detectedPeriod = list.firstOrNull()?.billingPeriod ?: ""
                        previewRows = list.take(4).map {
                            "${it.date}: ${it.shiftCode} (${String.format(Locale.GERMANY, "%.2f", it.billedHours)} Std., ÜS: ${String.format(Locale.GERMANY, "%.2f", it.billedOvertimeHours)})"
                        }
                    }
                }
            } catch (_: Exception) {
                detectedCount = 0
                detectedPeriod = ""
                previewRows = emptyList()
            }
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
                    .fillMaxHeight(0.88f)
                    .testTag("html_import_dialog"),
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
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "DPSA / HTML importieren",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Segmented Button to choose import target
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTarget == ImportTarget.PLANNED_SCHEDULE,
                            onClick = { selectedTarget = ImportTarget.PLANNED_SCHEDULE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("1. Dienst-Streifen")
                        }
                        SegmentedButton(
                            selected = selectedTarget == ImportTarget.BILLING_STATEMENT,
                            onClick = { selectedTarget = ImportTarget.BILLING_STATEMENT },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("2. Abrechnung")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons for picking file or inserting demo HTML
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { filePicker.launch("text/html") },
                            modifier = Modifier.weight(1f).testTag("select_html_file_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("HTML wählen")
                        }

                        OutlinedButton(
                            onClick = {
                                if (selectedTarget == ImportTarget.PLANNED_SCHEDULE) {
                                    htmlText = SampleHtmlData.samplePlannedScheduleHtml
                                    fileName = "Dienst-Streifen-08-2026.html"
                                } else {
                                    htmlText = SampleHtmlData.sampleBillingHtml
                                    fileName = "Stunden-Abrechnung-08-2026.html"
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("load_sample_html_btn")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("JA Salzburg Demo")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Text field for HTML content or pasting
                    OutlinedTextField(
                        value = htmlText,
                        onValueChange = { htmlText = it },
                        label = { Text("HTML-Inhalt oder DPSA Quelltext") },
                        placeholder = { Text("DPSA Dienst-Streifen oder Stunden-Abrechnung HTML einfügen...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("html_input_field"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Parser Detection Preview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (detectedCount > 0)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (detectedCount > 0) "Erkannt: $detectedCount Tage ($detectedPeriod)" else "Keine Einträge erkannt",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (detectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (detectedCount > 0) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (previewRows.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                previewRows.forEach { row ->
                                    Text(
                                        text = "• $row",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (detectedCount > previewRows.size) {
                                    Text(
                                        text = "... und ${detectedCount - previewRows.size} weitere",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dialog Action Buttons
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
                                if (selectedTarget == ImportTarget.PLANNED_SCHEDULE) {
                                    onImportPlanned(htmlText, fileName)
                                } else {
                                    onImportBilling(htmlText, fileName)
                                }
                                onDismiss()
                            },
                            enabled = detectedCount > 0,
                            modifier = Modifier.testTag("confirm_import_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("$detectedCount Tage importieren")
                        }
                    }
                }
            }
        }
    }
}
