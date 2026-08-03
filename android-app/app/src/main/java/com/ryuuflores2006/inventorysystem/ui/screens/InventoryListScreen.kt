package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.data.RetailGadget
import com.ryuuflores2006.inventorysystem.data.RepairPart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryListScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var branchFilter by remember { mutableStateOf("All Branches") }

    val scope = rememberCoroutineScope()
    var gadgets by remember { mutableStateOf<List<RetailGadget>>(emptyList()) }
    var parts by remember { mutableStateOf<List<RepairPart>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Fetch data
    LaunchedEffect(key1 = selectedTab, key2 = branchFilter) {
        isLoading = true
        scope.launch {
            if (selectedTab == 0) {
                val list = SupabaseHelper.getAllGadgets()
                gadgets = if (branchFilter == "All Branches") {
                    list
                } else {
                    list.filter { it.current_branch == branchFilter }
                }
            } else {
                val targetBranch = if (branchFilter == "All Branches") null else branchFilter
                parts = SupabaseHelper.getAllParts(targetBranch)
            }
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Branch Inventory",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24_sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Branch Selector & Search
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simple Branch Dropdown trigger
            var dropdownExpanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { dropdownExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(text = branchFilter, color = MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    listOf("All Branches", "Branch A", "Branch B", "Branch C").forEach { loc ->
                        DropdownMenuItem(
                            text = { Text(loc) },
                            onClick = {
                                branchFilter = loc
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Search query textfield
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search IMEI or SKU", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .height(52.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs: Serialized vs Bulk
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Serialized Phones", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Bulk Parts/Accs", fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            if (selectedTab == 0) {
                val filteredGadgets = gadgets.filter {
                    it.imei_1.contains(searchQuery, ignoreCase = true) ||
                            it.sku.contains(searchQuery, ignoreCase = true) ||
                            it.brand.contains(searchQuery, ignoreCase = true) ||
                            it.model.contains(searchQuery, ignoreCase = true)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredGadgets) { gadget ->
                        GadgetItemCard(gadget)
                    }
                }
            } else {
                val filteredParts = parts.filter {
                    it.sku.contains(searchQuery, ignoreCase = true) ||
                            it.part_name.contains(searchQuery, ignoreCase = true)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredParts) { part ->
                        PartItemCard(part)
                    }
                }
            }
        }
    }
}

@Composable
fun GadgetItemCard(gadget: RetailGadget) {
    val statusColor = when (gadget.status) {
        "In Stock" -> Color(0xFF22C55E)
        "In Transit" -> Color(0xFF3B82F6)
        "Sold" -> Color(0xFF94A3B8)
        else -> Color(0xFFF97316)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${gadget.brand} ${gadget.model}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = gadget.status,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("SKU: ${gadget.sku}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
            Text("Specs: ${gadget.storage} / ${gadget.ram} | ${gadget.color}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
            Text("IMEI 1: ${gadget.imei_1}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = gadget.current_branch,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "₱${gadget.retail_price}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun PartItemCard(part: RepairPart) {
    val isLowStock = part.stock_qty <= part.minimum_stock_threshold
    val stockColor = if (isLowStock) Color(0xFFEF4444) else Color(0xFF22C55E)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = part.part_name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(stockColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Qty: ${part.stock_qty}",
                        color = stockColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("SKU: ${part.sku}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
            Text("Compat: ${part.compatible_models.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = part.branch_location,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "₱${part.service_price}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
