package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.BranchStore
import com.ryuuflores2006.inventorysystem.data.LiveStore
import com.ryuuflores2006.inventorysystem.data.ServiceTicket
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.ui.components.*
import com.ryuuflores2006.inventorysystem.ui.theme.*
import kotlinx.coroutines.launch

/** The statuses a ticket can move through, in the order work actually happens. */
val TICKET_STATUSES = listOf(
    "Pending", "Diagnosing", "Waiting for Parts", "Repairing", "Ready", "Completed"
)

/**
 * Repairs, in two halves: the live queue of jobs on the bench, and the intake
 * form for a new one. The queue is driven by [LiveStore], so a technician
 * marking a phone "Ready" on another device updates this list at once.
 */
@Composable
fun RepairTicketScreen(onScanClick: (onScanned: (String) -> Unit) -> Unit) {
    var showIntake by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!showIntake) {
                ExtendedFloatingActionButton(
                    onClick = { showIntake = true },
                    containerColor = Cyan,
                    contentColor = Ink900,
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    text = { Text("New repair") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (showIntake) {
                RepairIntakeForm(
                    onScanClick = onScanClick,
                    onCancel = { showIntake = false },
                    onCreated = { branch ->
                        showIntake = false
                        snackbar.showSnackbar("Repair job registered at $branch")
                    }
                )
            } else {
                RepairQueue(snackbar)
            }
        }
    }
}

@Composable
private fun RepairQueue(snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var statusFilter by remember { mutableStateOf("Open") }

    val filters = remember { listOf("Open", "All") + TICKET_STATUSES }
    val tickets = remember(LiveStore.tickets, statusFilter) {
        when (statusFilter) {
            "All" -> LiveStore.tickets
            "Open" -> LiveStore.tickets.filter { it.ticket_status != "Completed" }
            else -> LiveStore.tickets.filter { it.ticket_status == statusFilter }
        }.sortedByDescending { it.created_at ?: "" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        ScreenHeader(
            title = "Repairs",
            subtitle = "${LiveStore.openTickets.size} open of ${LiveStore.tickets.size}",
            trailing = {
                IconButton(onClick = { scope.launch { LiveStore.refresh() } }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Ash)
                }
            }
        )

        FilterChipRow(options = filters, selected = statusFilter, onSelect = { statusFilter = it })

        Spacer(Modifier.height(12.dp))

        when {
            LiveStore.isLoading && !LiveStore.hasLoadedOnce -> LoadingCards()

            tickets.isEmpty() -> EmptyState(
                icon = Icons.Default.Build,
                title = if (statusFilter == "Open") "Nothing on the bench" else "No tickets here",
                message = if (statusFilter == "Open") {
                    "Tap “New repair” to book a customer device in."
                } else {
                    "No repair is currently marked “$statusFilter”."
                }
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(tickets, key = { it.ticket_id ?: (it.imei_serial + it.created_at) }) { ticket ->
                    TicketCard(ticket) { newStatus ->
                        val id = ticket.ticket_id
                        if (id != null) {
                            scope.launch {
                                val ok = SupabaseHelper.updateTicketStatus(id, newStatus)
                                if (ok) {
                                    LiveStore.refresh()
                                    snackbar.showSnackbar("${ticket.device_model} → $newStatus")
                                } else {
                                    snackbar.showSnackbar("Could not update that ticket.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: ServiceTicket, onStatusChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    AppCard(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(ticket.device_model, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${ticket.customer_name} · ${ticket.phone_number}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash
                )
            }
            Box(
                modifier = Modifier.clickable { menuOpen = true }
            ) {
                StatusPill(
                    text = ticket.ticket_status,
                    color = ticketStatusColor(ticket.ticket_status)
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(Ink600)
                ) {
                    TICKET_STATUSES.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            ticketStatusColor(status),
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                            },
                            onClick = {
                                menuOpen = false
                                if (status != ticket.ticket_status) onStatusChange(status)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            ticket.issue_description,
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
            maxLines = if (expanded) Int.MAX_VALUE else 2
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ticket.branch_location,
                style = MaterialTheme.typography.labelMedium,
                color = Cyan,
                modifier = Modifier.weight(1f)
            )
            Text(peso(ticket.total_amount), style = MaterialTheme.typography.titleLarge)
        }

        if (expanded) {
            HorizontalDivider(color = Ink500, modifier = Modifier.padding(vertical = 12.dp))
            DetailRow("IMEI", ticket.imei_serial)
            DetailRow("Technician", ticket.assigned_technician ?: "Unassigned")
            DetailRow("Labour", peso(ticket.labor_cost))
            DetailRow("Booked in", shortStamp(ticket.created_at))
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, Ink500)
            ) {
                Text("Change status")
            }
        }
    }
}

@Composable
private fun RepairIntakeForm(
    onScanClick: (onScanned: (String) -> Unit) -> Unit,
    onCancel: () -> Unit,
    onCreated: suspend (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var customerName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var deviceModel by remember { mutableStateOf("") }
    var imeiSerial by remember { mutableStateOf("") }
    var issueDescription by remember { mutableStateOf("") }
    var assignedTechnician by remember { mutableStateOf("") }
    var laborCostInput by remember { mutableStateOf("") }
    var selectedBranch by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (BranchStore.branches.isEmpty()) BranchStore.refresh()
    }
    LaunchedEffect(BranchStore.branches) {
        if (selectedBranch !in BranchStore.names) selectedBranch = BranchStore.defaultName
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(
            title = "New repair",
            subtitle = "Book a customer device in",
            trailing = {
                TextButton(onClick = onCancel) { Text("Cancel", color = Ash) }
            }
        )

        ErrorBanner(formError)

        AppDropdown(
            label = "Intake branch",
            selected = selectedBranch,
            options = BranchStore.names,
            onSelect = { selectedBranch = it },
            emptyHint = "No branches yet — add one first"
        )

        SectionLabel("Customer")
        AppTextField(customerName, { customerName = it }, "Name *")
        AppTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = "Phone number *",
            keyboardType = KeyboardType.Phone
        )

        SectionLabel("Device")
        AppTextField(deviceModel, { deviceModel = it }, "Model *", placeholder = "e.g. iPhone 13 Pro")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            AppTextField(
                value = imeiSerial,
                onValueChange = { imeiSerial = it },
                label = "IMEI / serial *",
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = { onScanClick { scanned -> imeiSerial = scanned } },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Ink600,
                    contentColor = Cyan
                ),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan serial")
            }
        }
        AppTextField(
            value = issueDescription,
            onValueChange = { issueDescription = it },
            label = "Reported issue *",
            singleLine = false,
            minLines = 3,
            placeholder = "What the customer says is wrong"
        )

        SectionLabel("Job")
        AppTextField(assignedTechnician, { assignedTechnician = it }, "Technician (optional)")
        AppTextField(
            value = laborCostInput,
            onValueChange = { laborCostInput = it },
            label = "Estimated labour (₱)",
            keyboardType = KeyboardType.Decimal
        )

        Spacer(Modifier.height(8.dp))

        PrimaryButton(
            text = "Register repair job",
            busy = isSubmitting,
            icon = Icons.Default.CheckCircle,
            onClick = {
                formError = null
                if (selectedBranch.isBlank()) {
                    formError = "Add a branch first, from the Branches tab."
                    return@PrimaryButton
                }
                if (customerName.isBlank() || phoneNumber.isBlank() || deviceModel.isBlank() ||
                    imeiSerial.isBlank() || issueDescription.isBlank()
                ) {
                    formError = "Fill in every field marked with *."
                    return@PrimaryButton
                }
                isSubmitting = true
                scope.launch {
                    val labor = laborCostInput.toDoubleOrNull() ?: 0.0
                    val ok = SupabaseHelper.createTicket(
                        ServiceTicket(
                            customer_name = customerName.trim(),
                            phone_number = phoneNumber.trim(),
                            device_model = deviceModel.trim(),
                            imei_serial = imeiSerial.trim(),
                            issue_description = issueDescription.trim(),
                            assigned_technician = assignedTechnician.takeIf { it.isNotBlank() },
                            ticket_status = "Pending",
                            labor_cost = labor,
                            // Starts equal to labour; grows as parts are assigned.
                            total_amount = labor,
                            branch_location = selectedBranch
                        )
                    )
                    isSubmitting = false
                    if (ok) {
                        LiveStore.refresh()
                        onCreated(selectedBranch)
                    } else {
                        formError = "Could not save the ticket. Check your connection."
                    }
                }
            }
        )

        Spacer(Modifier.height(24.dp))
    }
}
