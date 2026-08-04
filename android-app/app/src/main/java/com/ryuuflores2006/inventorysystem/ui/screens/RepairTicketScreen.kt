package com.ryuuflores2006.inventorysystem.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryuuflores2006.inventorysystem.data.BranchStore
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.data.ServiceTicket
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairTicketScreen(onScanClick: (onScanned: (String) -> Unit) -> Unit) {
    val context = LocalContext.current
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

    // Intake locations come from the shared `branches` table.
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
            .padding(16.dp)
    ) {
        Text(
            text = "Repair Service Intake",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = customerName,
            onValueChange = { customerName = it },
            label = { Text("Customer Name") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Customer Phone Number") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        OutlinedTextField(
            value = deviceModel,
            onValueChange = { deviceModel = it },
            label = { Text("Device Model") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedTextField(
                value = imeiSerial,
                onValueChange = { imeiSerial = it },
                label = { Text("Device IMEI / Serial") },
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Button(
                onClick = {
                    onScanClick { scannedVal ->
                        imeiSerial = scannedVal
                    }
                },
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text("Scan")
            }
        }

        OutlinedTextField(
            value = issueDescription,
            onValueChange = { issueDescription = it },
            label = { Text("Issue Description") },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().height(120.dp).padding(vertical = 4.dp)
        )

        OutlinedTextField(
            value = assignedTechnician,
            onValueChange = { assignedTechnician = it },
            label = { Text("Assigned Technician (Optional)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        OutlinedTextField(
            value = laborCostInput,
            onValueChange = { laborCostInput = it },
            label = { Text("Est. Labor Cost (₱)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Intake Branch Dropdown
        var branchExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { branchExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
            ) {
                Text(
                    if (selectedBranch.isBlank()) "Intake Location: none yet — add a branch first"
                    else "Intake Location: $selectedBranch"
                )
            }
            DropdownMenu(
                expanded = branchExpanded,
                onDismissRequest = { branchExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                BranchStore.names.forEach { loc ->
                    DropdownMenuItem(
                        text = { Text(loc) },
                        onClick = {
                            selectedBranch = loc
                            branchExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (selectedBranch.isBlank()) {
                    Toast.makeText(context, "Add a branch first (Branches tab)", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (customerName.isBlank() || phoneNumber.isBlank() || deviceModel.isBlank() || imeiSerial.isBlank() || issueDescription.isBlank()) {
                    Toast.makeText(context, "Please fill in all mandatory fields", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isSubmitting = true
                scope.launch {
                    val labor = laborCostInput.toDoubleOrNull() ?: 0.0
                    val ticket = ServiceTicket(
                        customer_name = customerName,
                        phone_number = phoneNumber,
                        device_model = deviceModel,
                        imei_serial = imeiSerial,
                        issue_description = issueDescription,
                        assigned_technician = assignedTechnician.takeIf { it.isNotBlank() },
                        ticket_status = "Pending",
                        labor_cost = labor,
                        total_amount = labor, // Starts equal to labor cost, dynamically increments as parts are assigned
                        branch_location = selectedBranch
                    )

                    val success = SupabaseHelper.createTicket(ticket)
                    isSubmitting = false
                    if (success) {
                        Toast.makeText(context, "Repair Ticket Created!", Toast.LENGTH_LONG).show()
                        customerName = ""
                        phoneNumber = ""
                        deviceModel = ""
                        imeiSerial = ""
                        issueDescription = ""
                        assignedTechnician = ""
                        laborCostInput = ""
                    } else {
                        Toast.makeText(context, "Error saving ticket. Check network.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isSubmitting,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("Register Repair Job", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
