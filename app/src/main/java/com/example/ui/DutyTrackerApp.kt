package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ActualShiftEntity
import com.example.data.PlannedShiftEntity
import com.example.ui.screens.*
import kotlinx.coroutines.launch

enum class AppTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OVERVIEW("6 Monate", Icons.Default.CalendarViewMonth),
    PLAN("Dienstplan", Icons.Default.CalendarMonth),
    SHIFT_CHANGE("Änderung", Icons.Default.EditCalendar),
    BILLING("Abrechnung", Icons.Default.ReceiptLong)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DutyTrackerApp(viewModel: ShiftViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val plannedShifts by viewModel.plannedShifts.collectAsStateWithLifecycle()
    val actualShifts by viewModel.actualShifts.collectAsStateWithLifecycle()
    val billingShifts by viewModel.billingShifts.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.OVERVIEW) }
    var showingMonthDetailFor by remember { mutableStateOf<String?>(null) }

    // Dialog state
    var showImportDialog by remember { mutableStateOf<ImportTarget?>(null) }
    var editingActualShift by remember { mutableStateOf<ActualShiftEntity?>(null) }
    var editingDateForNewShift by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showMonthMenu by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Listen for toast/user messages from ViewModel
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (currentTab == AppTab.OVERVIEW && showingMonthDetailFor != null) {
                        IconButton(
                            onClick = { showingMonthDetailFor = null },
                            modifier = Modifier.testTag("nav_back_to_6m")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zur 6-Monate-Übersicht")
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (currentTab == AppTab.OVERVIEW && showingMonthDetailFor != null) {
                                "Monatsabgleich: $showingMonthDetailFor"
                            } else if (currentTab == AppTab.OVERVIEW) {
                                "6-Monate-Übersicht"
                            } else if (currentTab == AppTab.PLAN) {
                                "Dienstplan (Monat)"
                            } else if (currentTab == AppTab.SHIFT_CHANGE) {
                                "Änderung"
                            } else {
                                "Abrechnung (Monat)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // Active Month Dropdown Trigger
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monat: ${uiState.selectedMonth.ifEmpty { "Alle" }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { showMonthMenu = true },
                                modifier = Modifier.size(24.dp).testTag("select_month_dropdown")
                            ) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Monat wählen")
                            }

                            DropdownMenu(
                                expanded = showMonthMenu,
                                onDismissRequest = { showMonthMenu = false }
                            ) {
                                uiState.availableMonths.forEach { month ->
                                    DropdownMenuItem(
                                        text = { Text(month) },
                                        onClick = {
                                            viewModel.selectMonth(month)
                                            showMonthMenu = false
                                        },
                                        leadingIcon = if (uiState.selectedMonth == month) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Quick Action: Load Sample Demo Data
                    IconButton(
                        onClick = { viewModel.loadDemoData() },
                        modifier = Modifier.testTag("topbar_load_demo_btn")
                    ) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = "Demo-Daten laden",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Overflow Menu
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("topbar_overflow_menu")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Dienstplan HTML importieren") },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showImportDialog = ImportTarget.PLANNED_SCHEDULE
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Abrechnung HTML importieren") },
                            leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showImportDialog = ImportTarget.BILLING_STATEMENT
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Beispiel-Monat (August 2026) laden") },
                            leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.loadDemoData()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Alle Daten löschen", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showClearConfirmation = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val badgeCount = when (tab) {
                        AppTab.OVERVIEW -> (uiState.sixMonthsSummary ?: uiState.twelveMonthsSummary)?.totalDiscrepanciesCount ?: 0
                        else -> 0
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (badgeCount > 0) {
                                BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.OVERVIEW -> {
                    if (showingMonthDetailFor == null) {
                        SixMonthsOverviewScreen(
                            uiState = uiState,
                            onSelectMonth = { month ->
                                viewModel.selectMonth(month)
                            },
                            onOpenMonthDetail = { month ->
                                viewModel.selectMonth(month)
                                showingMonthDetailFor = month
                            },
                            onOpenImportPlan = { showImportDialog = ImportTarget.PLANNED_SCHEDULE },
                            onOpenImportBilling = { showImportDialog = ImportTarget.BILLING_STATEMENT }
                        )
                    } else {
                        ReconciliationScreen(
                            uiState = uiState,
                            onFilterChange = { viewModel.setFilterType(it) },
                            onOpenImport = { showImportDialog = it },
                            onEditShift = { shift, defaultDate ->
                                editingActualShift = shift
                                editingDateForNewShift = defaultDate
                            },
                            onCopyPlanToActual = { reconciliation ->
                                reconciliation.planned?.let { viewModel.copyPlannedToActual(it) }
                            },
                            onLoadDemoData = { viewModel.loadDemoData() },
                            onBackToOverview = { showingMonthDetailFor = null }
                        )
                    }
                }
                AppTab.PLAN -> {
                    PlannedScheduleScreen(
                        plannedShifts = plannedShifts,
                        selectedMonth = uiState.selectedMonth,
                        onSelectMonth = { viewModel.selectMonth(it) },
                        onOpenImport = { showImportDialog = ImportTarget.PLANNED_SCHEDULE },
                        onCopySingleToActual = { viewModel.copyPlannedToActual(it) },
                        onCopyAllToActual = { viewModel.copyAllPlannedToActual(it) },
                        onDeleteShift = { viewModel.deletePlannedShift(it) }
                    )
                }
                AppTab.SHIFT_CHANGE -> {
                    ShiftChangeScreen(
                        selectedMonth = uiState.selectedMonth,
                        onSelectMonth = { viewModel.selectMonth(it) },
                        plannedShifts = plannedShifts,
                        actualShifts = actualShifts,
                        onApplyShiftChange = { date, newCode, additionalHours ->
                            viewModel.applyShiftChange(date, newCode, additionalHours)
                        },
                        onDeleteActualShift = { viewModel.deleteActualShift(it) }
                    )
                }
                AppTab.BILLING -> {
                    BillingScreen(
                        billingShifts = billingShifts,
                        dpsaSummary = uiState.dpsaSummary,
                        selectedMonth = uiState.selectedMonth,
                        onSelectMonth = { viewModel.selectMonth(it) },
                        onOpenImport = { showImportDialog = ImportTarget.BILLING_STATEMENT },
                        onDeleteShift = { viewModel.deleteBillingShift(it) }
                    )
                }
            }
        }
    }

    // Import Dialog
    showImportDialog?.let { target ->
        HtmlImportDialog(
            initialTarget = target,
            onDismiss = { showImportDialog = null },
            onImportPlanned = { html, filename ->
                viewModel.importPlannedScheduleHtml(html, filename)
            },
            onImportBilling = { html, filename ->
                viewModel.importBillingStatementHtml(html, filename)
            }
        )
    }

    // Shift Edit/Add Dialog
    if (editingActualShift != null || editingDateForNewShift != null) {
        ShiftEditDialog(
            initialShift = editingActualShift,
            defaultDate = editingDateForNewShift ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY).format(java.util.Date()),
            onDismiss = {
                editingActualShift = null
                editingDateForNewShift = null
            },
            onSave = { shift ->
                viewModel.saveActualShift(shift)
                editingActualShift = null
                editingDateForNewShift = null
            }
        )
    }

    // Clear confirmation dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Alle Daten löschen?") },
            text = { Text("Möchtest du alle Dienstpläne, erfassten Ist-Zeiten und Abrechnungen unwiderruflich löschen?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
