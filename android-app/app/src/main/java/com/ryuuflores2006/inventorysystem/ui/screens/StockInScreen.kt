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
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.data.RetailGadget
import com.ryuuflores2006.inventorysystem.data.RepairPart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockInScreen(onScanClick: (onScanned: (String) -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isSerialized by remember { mutableStateOf(true) }
    var selectedBranch by remember { mutableStateOf("Manila HQ") }

    // Fields for Serialized Track
    var sku by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var storage by remember { mutableStateOf("") }
    var ram by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var retailPrice by remember { mutableStateOf("") }
    var imei1 by remember { mutableStateOf("") }
    var imei2 by remember { mutableStateOf("") }
    var supplierName by remember { mutableStateOf("") }

    // Fields for Bulk Track
    var partName by remember { mutableStateOf("") }
    var compatibleModelsInput by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var minThreshold by remember { mutableStateOf("5") }

    var isSubmitting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Delivery Stock-In Log",
            fontSize = 24_sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Branch Selection
        var branchExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { branchExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
            ) {
                Text("Receiving Branch: $selectedBranch")
            }
            DropdownMenu(
                expanded = branchExpanded,
                onDismissRequest = { branchExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Manila HQ", "Cebu Outlet", "Davao Hub").forEach { loc ->
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

        Spacer(modifier = Modifier.height(12.dp))

        // Segmented Track Picker
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { isSerialized = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSerialized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                ) {
                    // Fallback block if needed
                }
            ) {
                Text(
                    "Serialized (Phone)",
                    color = if (isSerialized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { isSerialized = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isSerialized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                ) {
                    // Fallback block
                }
            ) {
                Text(
                    "Bulk Parts/Accs",
                    color = if (!isSerialized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isSerialized) {
            // Serialized inputs
            OutlinedTextField(
                value = sku,
                onValueChange = { sku = it },
                label = { Text("SKU") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Brand") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(
                    value = storage,
                    onValueChange = { storage = it },
                    label = { Text("Storage (e.g. 256GB)") },
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                OutlinedTextField(
                    value = ram,
                    onValueChange = { ram = it },
                    label = { Text("RAM (e.g. 8GB)") },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text("Color") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it },
                    label = { Text("Cost Price") },
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                OutlinedTextField(
                    value = retailPrice,
                    onValueChange = { retailPrice = it },
                    label = { Text("Retail Price") },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = imei1,
                    onValueChange = { imei1 = it },
                    label = { Text("Unique IMEI 1") },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Button(
                    onClick = {
                        onScanClick { scannedImei ->
                            imei1 = scannedImei
                        }
                    },
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text("Scan")
                }
            }
            OutlinedTextField(
                value = imei2,
                onValueChange = { imei2 = it },
                label = { Text("IMEI 2 (Optional)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = supplierName,
                onValueChange = { supplierName = it },
                label = { Text("Supplier Name") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        } else {
            // Bulk inputs
            OutlinedTextField(
                value = sku,
                onValueChange = { sku = it },
                label = { Text("Part SKU") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = partName,
                onValueChange = { partName = it },
                label = { Text("Part/Accessory Name") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = compatibleModelsInput,
                onValueChange = { compatibleModelsInput = it },
                label = { Text("Compatible Models (comma-separated)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Qty Received") },
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                OutlinedTextField(
                    value = minThreshold,
                    onValueChange = { minThreshold = it },
                    label = { Text("Min Stock Alert") },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it },
                    label = { Text("Cost Price") },
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                OutlinedTextField(
                    value = retailPrice,
                    onValueChange = { retailPrice = it },
                    label = { Text("Service/Retail Price") },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (sku.isBlank()) {
                    Toast.makeText(context, "SKU is required", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isSubmitting = true
                scope.launch {
                    val success = if (isSerialized) {
                        val cost = costPrice.toDoubleOrNull() ?: 0.0
                        val retail = retailPrice.toDoubleOrNull() ?: 0.0
                        if (imei1.length != 15) {
                            Toast.makeText(context, "IMEI 1 must be 15 digits", Toast.LENGTH_SHORT).show()
                            isSubmitting = false
                            return@launch
                        }
                        val gadget = RetailGadget(
                            sku = sku,
                            brand = brand,
                            model = model,
                            storage = storage,
                            ram = ram,
                            color = color,
                            cost_price = cost,
                            retail_price = retail,
                            current_branch = selectedBranch,
                            status = "In Stock",
                            imei_1 = imei1,
                            imei_2 = imei2.takeIf { it.isNotBlank() },
                            supplier_name = supplierName.takeIf { it.isNotBlank() }
                        )
                        SupabaseHelper.insertGadget(gadget)
                    } else {
                        val cost = costPrice.toDoubleOrNull() ?: 0.0
                        val service = retailPrice.toDoubleOrNull() ?: 0.0
                        val qty = quantity.toIntOrNull() ?: 1
                        val threshold = minThreshold.toIntOrNull() ?: 5
                        val compat = compatibleModelsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val part = RepairPart(
                            sku = sku,
                            part_name = partName,
                            compatible_models = compat,
                            branch_location = selectedBranch,
                            stock_qty = qty,
                            minimum_stock_threshold = threshold,
                            cost_price = cost,
                            service_price = service
                        )
                        SupabaseHelper.insertPartStock(part)
                    }

                    isSubmitting = false
                    if (success) {
                        Toast.makeText(context, "Stock logged successfully!", Toast.LENGTH_LONG).show()
                        // Reset forms
                        sku = ""
                        brand = ""
                        model = ""
                        storage = ""
                        ram = ""
                        color = ""
                        costPrice = ""
                        retailPrice = ""
                        imei1 = ""
                        imei2 = ""
                        supplierName = ""
                        partName = ""
                        compatibleModelsInput = ""
                        quantity = ""
                    } else {
                        Toast.makeText(context, "Failed to register stock. Check duplicate values or network.", Toast.LENGTH_LONG).show()
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
                Text("Confirm Stock-In Receipt", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
