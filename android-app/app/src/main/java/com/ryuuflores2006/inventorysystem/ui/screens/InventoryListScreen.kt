package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.BranchStore
import com.ryuuflores2006.inventorysystem.data.LiveStore
import com.ryuuflores2006.inventorysystem.data.RepairPart
import com.ryuuflores2006.inventorysystem.data.RetailGadget
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
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Ash)
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
                    containerColor = Ink600,
                    contentColor = Cyan
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
            contentColor = Cyan,
            divider = { HorizontalDivider(color = Ink600) }
        ) {
            Tab(
                selected = !showParts,
                onClick = { showParts = false },
                selectedContentColor = Cyan,
                unselectedContentColor = Ash,
                text = { Text("Devices (${LiveStore.gadgetsIn(branch).size})", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = showParts,
                onClick = { showParts = true },
                selectedContentColor = Cyan,
                unselectedContentColor = Ash,
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
                items(gadgets, key = { it.item_id ?: it.imei_1 }) { GadgetItemCard(it) }
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
fun GadgetItemCard(gadget: RetailGadget) {
    var expanded by remember { mutableStateOf(false) }

    AppCard(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${gadget.brand} ${gadget.model}", style = MaterialTheme.typography.titleLarge)
                Text(
                    listOf(gadget.storage, gadget.ram, gadget.color).filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash
                )
            }
            StatusPill(gadget.status, gadgetStatusColor(gadget.status))
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                gadget.current_branch,
                style = MaterialTheme.typography.labelMedium,
                color = Cyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(peso(gadget.retail_price), style = MaterialTheme.typography.titleLarge)
        }

        if (expanded) {
            HorizontalDivider(color = Ink500, modifier = Modifier.padding(vertical = 12.dp))
            DetailRow("SKU", gadget.sku)
            DetailRow("IMEI 1", gadget.imei_1)
            gadget.imei_2?.takeIf { it.isNotBlank() }?.let { DetailRow("IMEI 2", it) }
            DetailRow("Cost", peso(gadget.cost_price))
            DetailRow("Margin", peso(gadget.retail_price - gadget.cost_price), Emerald)
            gadget.supplier_name?.takeIf { it.isNotBlank() }?.let { DetailRow("Supplier", it) }
            DetailRow("Received", shortStamp(gadget.created_at))
        } else {
            Text(
                "IMEI ${gadget.imei_1}",
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun PartItemCard(part: RepairPart) {
    var expanded by remember { mutableStateOf(false) }
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
                Text(part.sku, style = MaterialTheme.typography.bodySmall, color = Ash)
            }
            StatusPill("${part.stock_qty} in stock", stockColor)
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                part.branch_location,
                style = MaterialTheme.typography.labelMedium,
                color = Cyan,
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
            HorizontalDivider(color = Ink500, modifier = Modifier.padding(vertical = 12.dp))
            DetailRow("Fits", part.compatible_models.joinToString(", ").ifBlank { "Not specified" })
            DetailRow("Cost", peso(part.cost_price))
            DetailRow("Margin", peso(part.service_price - part.cost_price), Emerald)
            DetailRow("Reorder at", part.minimum_stock_threshold.toString())
            DetailRow("Added", shortStamp(part.created_at))
        }
    }
}
