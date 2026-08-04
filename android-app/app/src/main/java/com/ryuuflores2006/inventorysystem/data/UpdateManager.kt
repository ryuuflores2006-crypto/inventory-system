package com.ryuuflores2006.inventorysystem.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater.
 *
 * Publish a row in `app_releases` with a higher `version_code` than the
 * installed APK and the app offers the update on next launch: it downloads the
 * APK to its own external files dir and hands it to the system package
 * installer. No Play Store involved.
 *
 * The new APK must be signed with the same key as the installed one or Android
 * refuses the update.
 */
object UpdateManager {

    /** Nothing → checking → available → downloading → ready → installing. */
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Available(val release: AppRelease) : State
        data class Downloading(val release: AppRelease, val progress: Float) : State
        data class Ready(val release: AppRelease, val file: File) : State
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /** Set when the user dismisses an optional update, so we stop nagging. */
    private var dismissedVersion: Int? = null

    fun installedVersionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        0L
    }

    fun installedVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }

    /**
     * Ask the server whether a newer build exists. Silent when it doesn't —
     * this runs on every launch, so it must never interrupt normal use.
     */
    suspend fun check(context: Context, manual: Boolean = false) {
        if (state is State.Downloading) return
        state = State.Checking

        val release = SupabaseHelper.getLatestRelease()
        if (release == null) {
            state = if (manual) State.Failed("Could not reach the update server.") else State.Idle
            return
        }

        val installed = installedVersionCode(context)
        state = when {
            release.version_code <= installed ->
                if (manual) State.Failed("You are on the latest version.") else State.Idle
            !manual && dismissedVersion == release.version_code -> State.Idle
            else -> State.Available(release)
        }
    }

    fun dismiss() {
        (state as? State.Available)?.let { dismissedVersion = it.release.version_code }
        state = State.Idle
    }

    fun clearError() {
        if (state is State.Failed) state = State.Idle
    }

    /** Download the APK, reporting progress, then move to [State.Ready]. */
    suspend fun download(context: Context, release: AppRelease) {
        state = State.Downloading(release, 0f)
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
                // Clear older downloads so the folder cannot grow without bound.
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "update-${release.version_code}.apk")

                val connection = (URL(release.apk_url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout = 60_000
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Server returned ${connection.responseCode}")
                }
                val total = connection.contentLength.toLong()

                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                state = State.Downloading(release, downloaded.toFloat() / total)
                            }
                        }
                    }
                }
                connection.disconnect()
                if (target.length() == 0L) throw IllegalStateException("Downloaded file was empty.")
                target
            }
            state = State.Ready(release, file)
        } catch (e: Exception) {
            e.printStackTrace()
            state = State.Failed(e.message ?: "Download failed.")
        }
    }

    /**
     * Hand the APK to the system installer. On Android 8+ the user must have
     * allowed this app to install packages; [canInstall] reports that, and
     * [settingsIntent] takes them to the toggle.
     */
    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun settingsIntent(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
