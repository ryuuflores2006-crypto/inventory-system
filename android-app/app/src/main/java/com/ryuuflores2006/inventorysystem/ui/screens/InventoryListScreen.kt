package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.BranchStore
import com.ryuuflores2006.inventorysystem.data.LiveStore
import com.ryuuflores2006.inventorysystem.data.RepairPart
import com.ryuuflores2006.inventorysystem.data.RetailGadget
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.ui.components.*
import com.ryuuflores2006.inventorysystem.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Stock across every store. Reads [LiveStore], so a sale rung up on another
 * phone or the PC dashboard changes this list without anyone pressing refresh.
 */
@Composable
fun InventoryListScreen(onScanClick: (onScanned: (String) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var showParts by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var branchFilter by remember { mutableStateOf(ALL_BRANCHES) }

    LaunchedEffect(BranchStore.branches) {
        if (branchFilter != ALL_BRANCHES && branchFilter !in BranchStore.names) {
            branchFilter = ALL_BRANCHES
        }
    }

    val branch = branchFilter.takeIf { it != ALL_BRANCHES }
    val query = searchQuery.trim()

    val gadgets = remember(LiveStore.gadgets, branch, query) {
        LiveStore.gadgetsIn(branch).filter { g ->
            query.isBlank() ||
                g.imei_1.contains(query, true) ||
                (g.imei_2?.contains(query, true) == true) ||
                g.sku.contains(query, true) ||
                g.brand.contains(query, true) ||
                g.model.contains(query, true)
        }.sortedBy { "${it.brand} ${it.model}" }
    }
    val parts = remember(LiveStore.parts, branch, query) {
        LiveStore.partsIn(branch).filter { p ->
            query.isBlank() ||
                p.sku.contains(query, true) ||
                p.part_name.contains(query, true) ||
                p.compatible_models.any { it.contains(query, true) }
        }.sortedBy { it.part_name }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        ScreenHeader(
            title = "Inventory",
            subtitle = if (branch == null) "All branches" else branch,
            trailing = {
                IconButton(onClick = { scope.launch { LiveStore.refresh() } }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = if (showParts) "Search part or SKU" else "Search IMEI, SKU or model",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = { onScanClick { scanned -> searchQuery = scanned } },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan to search")
            }
        }

        Spacer(Modifier.height(12.dp))

        BranchFilterChips(selected = branchFilter, onSelect = { branchFilter = it })

        Spacer(Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = if (showParts) 1 else 0,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) }
        ) {
            Tab(
                selected = !showParts,
                onClick = { showParts = false },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text("Devices (${LiveStore.gadgetsIn(branch).size})", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = showParts,
                onClick = { showParts = true },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text("Parts (${LiveStore.partsIn(branch).size})", fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            LiveStore.isLoading && !LiveStore.hasLoadedOnce -> LoadingCards()

            !showParts && gadgets.isEmpty() -> EmptyState(
                icon = if (query.isBlank()) Icons.Default.Inventory2 else Icons.Default.SearchOff,
                title = if (query.isBlank()) "No devices here yet" else "No match",
                message = if (query.isBlank()) {
                    "Serialized phones you receive in the Stock-In tab appear here."
                } else {
                    "Nothing matches “$query”."
                }
            )

            showParts && parts.isEmpty() -> EmptyState(
                icon = if (query.isBlank()) Icons.Default.Inventory2 else Icons.Default.SearchOff,
                title = if (query.isBlank()) "No parts here yet" else "No match",
                message = if (query.isBlank()) {
                    "Bulk parts and accessories you log in the Stock-In tab appear here."
                } else {
                    "Nothing matches “$query”."
                }
            )

            !showParts -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                val grouped = gadgets.groupBy { it.sku }
                items(grouped.keys.toList(), key = { it }) { sku ->
                    GadgetGroupCard(sku = sku, groupGadgets = grouped[sku]!!)
                }
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(parts, key = { it.part_id ?: (it.sku + it.branch_location) }) { PartItemCard(it) }
            }
        }
    }
}

@Composable
fun GadgetGroupCard(sku: String, groupGadgets: List<RetailGadget>) {
    var expanded by remember { mutableStateOf(false) }
    val first = groupGadgets.first()

    AppCard(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${first.brand} ${first.model}", style = MaterialTheme.typography.titleLarge)
                Text(
                    listOf(first.storage, first.ram, first.color).filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill("${groupGadgets.size} Units", Emerald)
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                first.current_branch,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(peso(first.retail_price), style = MaterialTheme.typography.titleLarge)
        }

        if (expanded) {
            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 12.dp))
            DetailRow("SKU", sku)
            DetailRow("Cost", peso(first.cost_price))
            DetailRow("Margin", peso(first.retail_price - first.cost_price), Emerald)
            
            Spacer(Modifier.height(12.dp))
            Text("Individual Units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            
            groupGadgets.forEach { gadget ->
                GadgetUnitRow(gadget)
            }
        }
    }
}

@Composable
fun GadgetUnitRow(gadget: RetailGadget) {
    var showEdit by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "IMEI: ${gadget.imei_1}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            StatusPill(gadget.status, gadgetStatusColor(gadget.status))
        }
        
        gadget.imei_2?.takeIf { it.isNotBlank() }?.let {
            Text("IMEI 2: $it", style = MaterialTheme.typography.bodySmall, color = Slate)
        }
        gadget.supplier_name?.takeIf { it.isNotBlank() }?.let {
            Text("Supplier: $it", style = MaterialTheme.typography.bodySmall, color = Slate)
        }
        Text("Received: ${shortStamp(gadget.created_at)}", style = MaterialTheme.typography.bodySmall, color = Slate)

        RemoveRow(
            what = "${gadget.brand} ${gadget.model}",
            detail = "IMEI ${gadget.imei_1}",
            blocked = when (gadget.status) {
                "Sold", "In Transit" -> "A ${gadget.status.lowercase()} unit cannot be deleted."
                else -> null
            },
            onConfirm = { SupabaseHelper.deleteGadget(gadget) },
            onEdit = { showEdit = true }
        )
    }

    if (showEdit) {
        EditGadgetDialog(
            gadget = gadget,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                val err = SupabaseHelper.updateGadget(updated)
                if (err == null) LiveStore.refresh()
                err
            }
        )
    }
}

@Composable
fun PartItemCard(part: RepairPart) {
    var expanded by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    val isLow = part.stock_qty <= part.minimum_stock_threshold
    val stockColor = when {
        part.stock_qty == 0 -> Rose
        isLow -> Amber
        else -> Emerald
    }

    AppCard(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(part.part_name, style = MaterialTheme.typography.titleLarge)
                Text(part.sku, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill("${part.stock_qty} in stock", stockColor)
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                part.branch_location,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(peso(part.service_price), style = MaterialTheme.typography.titleLarge)
        }

        if (isLow) {
            Text(
                "At or below the reorder point of ${part.minimum_stock_threshold}",
                style = MaterialTheme.typography.bodySmall,
                color = stockColor,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (expanded) {
            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 12.dp))
            DetailRow("Fits", part.compatible_models.joinToString(", ").ifBlank { "Not specified" })
            DetailRow("Cost", peso(part.cost_price))
            DetailRow("Margin", peso(part.service_price - part.cost_price), Emerald)
            DetailRow("Reorder at", part.minimum_stock_threshold.toString())
            DetailRow("Added", shortStamp(part.created_at))
            RemoveRow(
                what = part.part_name,
                detail = "${part.sku} at ${part.branch_location}",
                blocked = null,
                onConfirm = { SupabaseHelper.deletePart(part) },
                onEdit = { showEdit = true }
            )
        }
    }

    if (showEdit) {
        EditPartDialog(
            part = part,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                val err = SupabaseHelper.updatePart(updated)
                if (err == null) LiveStore.refresh()
                err
            }
        )
    }
}

/**
 * The delete affordance, kept behind the expanded card so it takes a
 * deliberate tap to reach and a second one to mean it. [blocked] states why
 * a row cannot go instead of hiding the button and leaving people guessing.
 */
@Composable
private fun RemoveRow(
    what: String,
    detail: String,
    blocked: String?,
    onConfirm: suspend () -> String?,
    onEdit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 8.dp))

    if (blocked != null) {
        Text(blocked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Rose)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { asking = true }, enabled = !busy) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Rose)
            Spacer(Modifier.width(6.dp))
            Text("Remove", color = Rose)
        }
        TextButton(onClick = onEdit, enabled = !busy) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Edit")
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { if (!busy) asking = false },
            title = { Text("Remove $what?") },
            text = { Text("$detail will be deleted for good, on every device.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val failure = onConfirm()
                            busy = false
                            asking = false
                            error = failure
                            if (failure == null) LiveStore.refresh()
                        }
                    }
                ) { Text("Delete", color = Rose) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }, enabled = !busy) { Text("Keep") }
            }
        )
    }
}

@Composable
fun EditGadgetDialog(
    gadget: RetailGadget,
    onDismiss: () -> Unit,
    onSave: suspend (RetailGadget) -> String?
) {
    var sku by remember { mutableStateOf(gadget.sku) }
    var brand by remember { mutableStateOf(gadget.brand) }
    var model by remember { mutableStateOf(gadget.model) }
    var storage by remember { mutableStateOf(gadget.storage) }
    var ram by remember { mutableStateOf(gadget.ram) }
    var color by remember { mutableStateOf(gadget.color) }
    var costPrice by remember { mutableStateOf(gadget.cost_price.toString()) }
    var retailPrice by remember { mutableStateOf(gadget.retail_price.toString()) }
    var imei1 by remember { mutableStateOf(gadget.imei_1) }
    var imei2 by remember { mutableStateOf(gadget.imei_2 ?: "") }
    var supplier by remember { mutableStateOf(gadget.supplier_name ?: "") }
    
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit Device") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = Rose, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") })
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") })
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") })
                OutlinedTextField(value = storage, onValueChange = { storage = it }, label = { Text("Storage") })
                OutlinedTextField(value = ram, onValueChange = { ram = it }, label = { Text("RAM") })
                OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color") })
                OutlinedTextField(value = costPrice, onValueChange = { costPrice = it }, label = { Text("Cost Price") })
                OutlinedTextField(value = retailPrice, onValueChange = { retailPrice = it }, label = { Text("Retail Price") })
                OutlinedTextField(value = imei1, onValueChange = { imei1 = it }, label = { Text("IMEI 1") })
                OutlinedTextField(value = imei2, onValueChange = { imei2 = it }, label = { Text("IMEI 2") })
                OutlinedTextField(value = supplier, onValueChange = { supplier = it }, label = { Text("Supplier") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    scope.launch {
                        saving = true
                        val updated = gadget.copy(
                            sku = sku,
                            brand = brand,
                            model = model,
                            storage = storage,
                            ram = ram,
                            color = color,
                            cost_price = costPrice.toDoubleOrNull() ?: 0.0,
                            retail_price = retailPrice.toDoubleOrNull() ?: 0.0,
                            imei_1 = imei1,
                            imei_2 = imei2.takeIf { it.isNotBlank() },
                            supplier_name = supplier.takeIf { it.isNotBlank() }
                        )
                        val err = onSave(updated)
                        saving = false
                        if (err != null) error = err else onDismiss()
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
fun EditPartDialog(
    part: RepairPart,
    onDismiss: () -> Unit,
    onSave: suspend (RepairPart) -> String?
) {
    var sku by remember { mutableStateOf(part.sku) }
    var name by remember { mutableStateOf(part.part_name) }
    var models by remember { mutableStateOf(part.compatible_models.joinToString(", ")) }
    var qty by remember { mutableStateOf(part.stock_qty.toString()) }
    var minQty by remember { mutableStateOf(part.minimum_stock_threshold.toString()) }
    var costPrice by remember { mutableStateOf(part.cost_price.toString()) }
    var servicePrice by remember { mutableStateOf(part.service_price.toString()) }
    
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit Part") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = Rose, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") })
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Part Name") })
                OutlinedTextField(value = models, onValueChange = { models = it }, label = { Text("Compatible Models (comma separated)") })
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Stock Quantity") })
                OutlinedTextField(value = minQty, onValueChange = { minQty = it }, label = { Text("Min Threshold") })
                OutlinedTextField(value = costPrice, onValueChange = { costPrice = it }, label = { Text("Cost Price") })
                OutlinedTextField(value = servicePrice, onValueChange = { servicePrice = it }, label = { Text("Service Price") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    scope.launch {
                        saving = true
                        val updated = part.copy(
                            sku = sku,
                            part_name = name,
                            compatible_models = models.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            stock_qty = qty.toIntOrNull() ?: 0,
                            minimum_stock_threshold = minQty.toIntOrNull() ?: 0,
                            cost_price = costPrice.toDoubleOrNull() ?: 0.0,
                            service_price = servicePrice.toDoubleOrNull() ?: 0.0
                        )
                        val err = onSave(updated)
                        saving = false
                        if (err != null) error = err else onDismiss()
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
