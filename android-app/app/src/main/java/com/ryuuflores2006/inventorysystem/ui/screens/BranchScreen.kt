package com.ryuuflores2006.inventorysystem.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryuuflores2006.inventorysystem.data.Branch
import com.ryuuflores2006.inventorysystem.data.BranchStore
import kotlinx.coroutines.launch

/**
 * Add and manage stores from the phone. The same list drives the PC dashboard.
 */
@Composable
fun BranchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingArchive by remember { mutableStateOf<Branch?>(null) }

    LaunchedEffect(Unit) { BranchStore.refresh() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add branch") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Branches",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Stores added here appear instantly in every dropdown, on the phone and on the PC dashboard.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            if (BranchStore.isLoading && BranchStore.all.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (BranchStore.all.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No branches yet. Tap “Add branch” to create your first store.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BranchStore.branches, key = { it.branch_id ?: it.name }) { branch ->
                        BranchCard(
                            branch = branch,
                            actionLabel = "Archive",
                            onAction = { pendingArchive = branch }
                        )
                    }

                    if (BranchStore.archived.isNotEmpty()) {
                        item {
                            Text(
                                "Archived",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(BranchStore.archived, key = { it.branch_id ?: it.name }) { branch ->
                            BranchCard(
                                branch = branch,
                                actionLabel = "Restore",
                                dimmed = true,
                                onAction = {
                                    scope.launch {
                                        val err = BranchStore.setActive(branch, true)
                                        Toast.makeText(
                                            context,
                                            err ?: "${branch.name} restored",
                                            Toast.LENGTH_SHORT
                                        ).show()
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
            title = { Text("Archive ${branch.name}?") },
            text = {
                Text(
                    "It disappears from every dropdown on the phone and the PC dashboard. " +
                        "Its stock, tickets and history are kept, and you can restore it " +
                        "from the Archived list below."
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingArchive = null
                    scope.launch {
                        val err = BranchStore.setActive(branch, false)
                        Toast.makeText(
                            context,
                            err ?: "${branch.name} archived",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) { Text("Archive") }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddDialog) {
        AddBranchDialog(
            onDismiss = { showAddDialog = false },
            onAdded = { name ->
                showAddDialog = false
                Toast.makeText(context, "$name added", Toast.LENGTH_SHORT).show()
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
    val alpha = if (dimmed) 0.55f else 1f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (branch.is_main) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Main store",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        branch.name,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                }
                val details = listOfNotNull(branch.code, branch.address, branch.phone)
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                }
            }
            TextButton(onClick = onAction) { Text(actionLabel) }
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
        title = { Text("Add branch") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Store name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Short code (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact number (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && name.isNotBlank(),
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
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}
