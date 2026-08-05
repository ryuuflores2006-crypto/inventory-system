package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.Branch
import com.ryuuflores2006.inventorysystem.data.BranchStore
import com.ryuuflores2006.inventorysystem.data.LiveStore
import com.ryuuflores2006.inventorysystem.ui.components.*
import com.ryuuflores2006.inventorysystem.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Add and manage stores from the phone. The same list drives every dropdown
 * here and the PC dashboard.
 */
@Composable
fun BranchScreen() {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingArchive by remember { mutableStateOf<Branch?>(null) }

    LaunchedEffect(Unit) { BranchStore.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Cyan,
                contentColor = DeepSpace,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add branch") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            ScreenHeader(
                title = "Branches",
                subtitle = "Stores added here appear instantly in every dropdown, on the phone and on the PC dashboard."
            )

            when {
                BranchStore.isLoading && BranchStore.all.isEmpty() -> LoadingCards(count = 3)

                BranchStore.all.isEmpty() -> EmptyState(
                    icon = Icons.Default.Storefront,
                    title = "No stores yet",
                    message = "Add your first branch and it becomes available everywhere in the system.",
                    actionLabel = "Add branch",
                    onAction = { showAddDialog = true }
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(BranchStore.branches, key = { it.branch_id ?: it.name }) { branch ->
                        BranchCard(
                            branch = branch,
                            actionLabel = "Archive",
                            onAction = { pendingArchive = branch }
                        )
                    }

                    if (BranchStore.archived.isNotEmpty()) {
                        item { SectionLabel("Archived") }
                        items(BranchStore.archived, key = { it.branch_id ?: it.name }) { branch ->
                            BranchCard(
                                branch = branch,
                                actionLabel = "Restore",
                                dimmed = true,
                                onAction = {
                                    scope.launch {
                                        val err = BranchStore.setActive(branch, true)
                                        snackbar.showSnackbar(err ?: "${branch.name} restored")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingArchive?.let { branch ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            containerColor = GlassSurface,
            title = { Text("Archive ${branch.name}?") },
            text = {
                Text(
                    "It disappears from every dropdown on the phone and the PC dashboard. " +
                        "Its stock, tickets and history are kept, and you can restore it " +
                        "from the Archived list below.",
                    color = Ash
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Chalk),
                    onClick = {
                        pendingArchive = null
                        scope.launch {
                            val err = BranchStore.setActive(branch, false)
                            snackbar.showSnackbar(err ?: "${branch.name} archived")
                        }
                    }
                ) { Text("Archive") }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text("Cancel", color = Ash) }
            }
        )
    }

    if (showAddDialog) {
        AddBranchDialog(
            onDismiss = { showAddDialog = false },
            onAdded = { name ->
                showAddDialog = false
                scope.launch { snackbar.showSnackbar("$name added") }
            }
        )
    }
}

@Composable
private fun BranchCard(
    branch: Branch,
    actionLabel: String,
    dimmed: Boolean = false,
    onAction: () -> Unit
) {
    // What this store is actually holding right now, straight from the live store.
    val devices = LiveStore.gadgetsIn(branch.name).size
    val parts = LiveStore.partsIn(branch.name).size
    val open = LiveStore.ticketsIn(branch.name).count { it.ticket_status != "Completed" }

    AppCard(modifier = Modifier.alpha(if (dimmed) 0.55f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (branch.is_main) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Main store",
                            tint = Amber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(branch.name, style = MaterialTheme.typography.titleLarge)
                }
                val details = listOfNotNull(branch.code, branch.address, branch.phone)
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ash,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            TextButton(onClick = onAction) {
                Text(actionLabel, color = if (dimmed) Cyan else Ash)
            }
        }

        if (!dimmed) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("$devices devices", Cyan, dense = true)
                StatusPill("$parts part lines", Azure, dense = true)
                StatusPill("$open open repairs", if (open > 0) Violet else Slate, dense = true)
            }
        }
    }
}

@Composable
private fun AddBranchDialog(onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = GlassSurface,
        title = { Text("Add branch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(name, { name = it }, "Store name *")
                AppTextField(code, { code = it }, "Short code (optional)")
                AppTextField(address, { address = it }, "Address (optional)")
                AppTextField(phone, { phone = it }, "Contact number (optional)")
                ErrorBanner(error)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = DeepSpace),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val err = BranchStore.add(name, code, address, phone)
                        busy = false
                        if (err == null) onAdded(name.trim()) else error = err
                    }
                }
            ) {
                Text(if (busy) "Saving…" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = Ash) }
        }
    )
}
