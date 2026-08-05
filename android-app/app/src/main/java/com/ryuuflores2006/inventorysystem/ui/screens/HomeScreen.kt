package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.BranchStore
import com.ryuuflores2006.inventorysystem.data.LiveStore
import com.ryuuflores2006.inventorysystem.ui.components.*
import com.ryuuflores2006.inventorysystem.ui.theme.*
import kotlinx.coroutines.launch

/**
 * The screen staff land on: how much is in stock, what is running out, and
 * which repairs are still open — for one branch or all of them at once.
 *
 * Everything here reads [LiveStore], so it redraws by itself the moment a row
 * changes on any device.
 */
@Composable
fun HomeScreen(onOpenInventory: () -> Unit, onOpenRepairs: () -> Unit) {
    val scope = rememberCoroutineScope()
    var branchFilter by remember { mutableStateOf(ALL_BRANCHES) }

    // A branch can be archived while it is selected; fall back to "all".
    LaunchedEffect(BranchStore.branches) {
        if (branchFilter != ALL_BRANCHES && branchFilter !in BranchStore.names) {
            branchFilter = ALL_BRANCHES
        }
    }

    val branch = branchFilter.takeIf { it != ALL_BRANCHES }
    val gadgets = LiveStore.gadgetsIn(branch)
    val parts = LiveStore.partsIn(branch)
    val tickets = LiveStore.ticketsIn(branch)

    val inStock = gadgets.count { it.status == "In Stock" }
    val inTransit = gadgets.count { it.status == "In Transit" }
    val lowStock = parts.filter { it.stock_qty <= it.minimum_stock_threshold }
    val openTickets = tickets.filter { it.ticket_status != "Completed" }
    val stockValue = gadgets.filter { it.status == "In Stock" }.sumOf { it.retail_price }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "Dashboard",
                subtitle = if (branch == null) "All branches" else branch,
                trailing = {
                    IconButton(onClick = { scope.launch { LiveStore.refresh() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        item {
            BranchFilterChips(
                selected = branchFilter,
                onSelect = { branchFilter = it }
            )
        }

        if (LiveStore.isLoading && !LiveStore.hasLoadedOnce) {
            item { LoadingCards(count = 3, modifier = Modifier.padding(top = 12.dp)) }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Devices in stock",
                    value = inStock.toString(),
                    caption = peso(stockValue) + " retail",
                    icon = Icons.Default.Inventory2,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenInventory
                )
                StatCard(
                    label = "Open repairs",
                    value = openTickets.size.toString(),
                    caption = "${tickets.size} total",
                    icon = Icons.Default.Build,
                    tint = Violet,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenRepairs
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Low stock parts",
                    value = lowStock.size.toString(),
                    caption = "${parts.size} part lines",
                    icon = Icons.Default.WarningAmber,
                    tint = if (lowStock.isEmpty()) Emerald else Amber,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenInventory
                )
                StatCard(
                    label = "In transit",
                    value = inTransit.toString(),
                    caption = "${LiveStore.transfers.count { it.transfer_status == "In Transit" }} transfers",
                    icon = Icons.Default.LocalShipping,
                    tint = Azure,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (gadgets.isEmpty() && parts.isEmpty() && tickets.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Storefront,
                    title = "Nothing logged yet",
                    message = if (branch == null) {
                        "Receive your first delivery from the Stock-In tab and it will show up here straight away."
                    } else {
                        "$branch has no stock or repairs yet."
                    }
                )
            }
            return@LazyColumn
        }

        if (lowStock.isNotEmpty()) {
            item { SectionLabel("Reorder soon") }
            items(lowStock.take(6), key = { it.part_id ?: it.sku }) { part ->
                AppCard(contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(part.part_name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${part.branch_location} · ${part.sku}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = "${part.stock_qty} left",
                            color = if (part.stock_qty == 0) Rose else Amber
                        )
                    }
                }
            }
            if (lowStock.size > 6) {
                item {
                    Text(
                        "+ ${lowStock.size - 6} more running low",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (openTickets.isNotEmpty()) {
            item { SectionLabel("Repairs in progress") }
            items(openTickets.take(6), key = { it.ticket_id ?: it.imei_serial }) { ticket ->
                AppCard(contentPadding = PaddingValues(14.dp), onClick = onOpenRepairs) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ticket.device_model, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${ticket.customer_name} · ${ticket.branch_location}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = ticket.ticket_status,
                            color = ticketStatusColor(ticket.ticket_status)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** The label used for "no branch filter" across every screen. */
const val ALL_BRANCHES = "All branches"

/** Branch chips shared by the dashboard and the inventory list. */
@Composable
fun BranchFilterChips(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val options = remember(BranchStore.branches) { listOf(ALL_BRANCHES) + BranchStore.names }
    if (options.size <= 1) return
    FilterChipRow(options = options, selected = selected, onSelect = onSelect, modifier = modifier)
}

/** Small right-aligned count used in a few headers. */
@Composable
fun CountLabel(count: Int, noun: String) {
    Text(
        "$count $noun",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}
