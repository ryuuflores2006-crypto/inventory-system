package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.UpdateManager
import kotlinx.coroutines.launch

/**
 * The whole in-app update flow, driven by [UpdateManager.state]: offer →
 * download with progress → hand off to the system package installer.
 *
 * It renders nothing while idle or checking, so it is safe to leave mounted.
 */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    when (val state = UpdateManager.state) {
        is UpdateManager.State.Idle, is UpdateManager.State.Checking -> Unit

        is UpdateManager.State.Available -> {
            val release = state.release
            AlertDialog(
                onDismissRequest = { if (!release.is_mandatory) UpdateManager.dismiss() },
                containerColor = MaterialTheme.colorScheme.surface,
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Update to ${release.version_name}") },
                text = {
                    Column {
                        Text(
                            release.release_notes?.takeIf { it.isNotBlank() }
                                ?: "A newer build of the app is available.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You are on ${UpdateManager.installedVersionName(context)}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (release.is_mandatory) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "This update is required.",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        onClick = { scope.launch { UpdateManager.download(context, release) } }
                    ) { Text("Download") }
                },
                dismissButton = {
                    if (!release.is_mandatory) {
                        TextButton(onClick = { UpdateManager.dismiss() }) { Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            )
        }

        is UpdateManager.State.Downloading -> {
            AlertDialog(
                onDismissRequest = { },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Downloading ${state.release.version_name}") },
                text = {
                    Column {
                        if (state.progress > 0f) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${(state.progress * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                },
                confirmButton = { }
            )
        }

        is UpdateManager.State.Ready -> {
            val allowed = UpdateManager.canInstall(context)
            AlertDialog(
                onDismissRequest = { },
                containerColor = MaterialTheme.colorScheme.surface,
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Ready to install") },
                text = {
                    Text(
                        if (allowed) {
                            "Android will ask you to confirm. The app closes during the install and " +
                                "reopens on ${state.release.version_name}."
                        } else {
                            "Android needs permission to install apps from this app. " +
                                "Turn it on, then come back and tap Install."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        onClick = {
                            if (allowed) {
                                UpdateManager.install(context, state.file)
                            } else {
                                context.startActivity(UpdateManager.settingsIntent(context))
                            }
                        }
                    ) { Text(if (allowed) "Install" else "Open settings") }
                },
                dismissButton = {
                    TextButton(onClick = { UpdateManager.dismiss() }) { Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            )
        }

        is UpdateManager.State.Failed -> {
            AlertDialog(
                onDismissRequest = { UpdateManager.clearError() },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Update") },
                text = { Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    TextButton(onClick = { UpdateManager.clearError() }) { Text("OK", color = MaterialTheme.colorScheme.primary) }
                }
            )
        }
    }
}
