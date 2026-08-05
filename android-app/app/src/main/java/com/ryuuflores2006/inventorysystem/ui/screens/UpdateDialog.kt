package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.UpdateManager
import com.ryuuflores2006.inventorysystem.ui.theme.Ash
import com.ryuuflores2006.inventorysystem.ui.theme.Cyan
import com.ryuuflores2006.inventorysystem.ui.theme.GlassSurfaceRaised
import com.ryuuflores2006.inventorysystem.ui.theme.GlassSurface
import com.ryuuflores2006.inventorysystem.ui.theme.DeepSpace
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
                containerColor = GlassSurface,
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Cyan) },
                title = { Text("Update to ${release.version_name}") },
                text = {
                    Column {
                        Text(
                            release.release_notes?.takeIf { it.isNotBlank() }
                                ?: "A newer build of the app is available.",
                            color = Ash,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You are on ${UpdateManager.installedVersionName(context)}.",
                            color = Ash,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (release.is_mandatory) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "This update is required.",
                                color = Cyan,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = DeepSpace),
                        onClick = { scope.launch { UpdateManager.download(context, release) } }
                    ) { Text("Download") }
                },
                dismissButton = {
                    if (!release.is_mandatory) {
                        TextButton(onClick = { UpdateManager.dismiss() }) { Text("Later", color = Ash) }
                    }
                }
            )
        }

        is UpdateManager.State.Downloading -> {
            AlertDialog(
                onDismissRequest = { },
                containerColor = GlassSurface,
                title = { Text("Downloading ${state.release.version_name}") },
                text = {
                    Column {
                        if (state.progress > 0f) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Cyan,
                                trackColor = GlassSurfaceRaised
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${(state.progress * 100).toInt()}%",
                                color = Ash,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Cyan,
                                trackColor = GlassSurfaceRaised
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
                containerColor = GlassSurface,
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Cyan) },
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
                        color = Ash,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = DeepSpace),
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
                    TextButton(onClick = { UpdateManager.dismiss() }) { Text("Later", color = Ash) }
                }
            )
        }

        is UpdateManager.State.Failed -> {
            AlertDialog(
                onDismissRequest = { UpdateManager.clearError() },
                containerColor = GlassSurface,
                title = { Text("Update") },
                text = { Text(state.message, color = Ash) },
                confirmButton = {
                    TextButton(onClick = { UpdateManager.clearError() }) { Text("OK", color = Cyan) }
                }
            )
        }
    }
}
