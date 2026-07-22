package com.ahamai.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ahamai.app.data.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps in-flight work (chat streaming AND agent runs) alive when the app is
 * minimized.
 *
 * How it works:
 * - All long-running work registers itself in [RunningTasks].
 * - When the app goes to the background while something is running, we promptly
 *   start [AhamAIService] as a foreground service in "keep-alive" mode. A
 *   foreground service stops Android from killing the process, so the coroutines
 *   that are already streaming the response keep running in-process and the UI
 *   simply picks up where it left off when the user returns.
 * - When the app comes back to the foreground we stop the keep-alive service.
 *
 * This replaces the old approach that waited 10 seconds and then re-issued the
 * request from the service (which lost in-flight agent work and could double up
 * requests).
 *
 * Additionally, when the app goes to the background (no active tasks), we
 * automatically export all workspaces to the device's Downloads/AhamAI folder.
 * This ensures the user's work survives app uninstall even if they forget to
 * manually export — the zips stay on-device in a public folder.
 */
object BackgroundTaskManager {

    // How quickly we promote to a foreground service after the app is backgrounded.
    // Kept short so the OS can't reap the process (and cut a response) before the
    // foreground service is up, e.g. when the user swipes the app away from recents.
    private const val PROMOTE_DELAY_MS = 400L

    // Delay before auto-exporting workspaces after the app goes to background.
    // Long enough to avoid exporting during a quick app-switch, but short enough
    // that the export runs before the OS may kill the process.
    private const val AUTO_BACKUP_DELAY_MS = 5_000L

    // How often (millis) we auto-backup at most — once per day is plenty.
    private const val AUTO_BACKUP_COOLDOWN_MS = 24 * 60 * 60 * 1000L

    private const val PREFS_KEY_LAST_AUTO_BACKUP = "last_auto_backup_ms"

    /** True while any chat or agent task is actively working. */
    val isBusy: Boolean get() = RunningTasks.hasAny

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Observes the whole-app (process) lifecycle.
     * - Keeps the process alive while work is running in the background.
     * - Auto-exports all workspaces to Downloads when the app backgrounds and
     *   no active tasks are running (so the export doesn't compete for I/O).
     */
    fun createLifecycleObserver(context: Context): DefaultLifecycleObserver {
        val appContext = context.applicationContext
        return object : DefaultLifecycleObserver {
            private var promoteJob: Job? = null
            private var backupJob: Job? = null

            override fun onStop(owner: LifecycleOwner) {
                // App moved to the background.
                if (RunningTasks.hasAny) {
                    promoteJob?.cancel()
                    promoteJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(PROMOTE_DELAY_MS)
                        if (RunningTasks.hasAny) {
                            AhamAIService.startKeepAlive(appContext)
                        }
                    }
                }

                // ── Auto-backup workspaces ────────────────────────────────────
                // Schedule an export of all workspaces to Downloads/AhamAI.
                // This way, if the user uninstalls the app, their workspace data
                // still exists on-device in a public folder they can find.
                // We use a cooldown so it doesn't run on every single pause.
                backupJob?.cancel()
                backupJob = CoroutineScope(Dispatchers.IO).launch {
                    delay(AUTO_BACKUP_DELAY_MS)

                    // Skip if tasks are still running — don't compete for files
                    if (RunningTasks.hasAny) return@launch

                    val prefs = appContext.getSharedPreferences("ahamai_prefs", Context.MODE_PRIVATE)
                    val lastBackup = prefs.getLong(PREFS_KEY_LAST_AUTO_BACKUP, 0L)
                    val now = System.currentTimeMillis()
                    if (now - lastBackup < AUTO_BACKUP_COOLDOWN_MS) return@launch

                    // Check there's actually something to back up
                    val projects = ProjectManager.listProjects(appContext)
                    if (projects.isEmpty()) return@launch

                    val result = ProjectManager.exportAllWorkspaceHistory(appContext)
                    if (result.startsWith("OK:")) {
                        prefs.edit().putLong(PREFS_KEY_LAST_AUTO_BACKUP, now).apply()
                        android.util.Log.i("AhamAI-Backup", "Auto-backup succeeded: $result")
                    } else {
                        android.util.Log.w("AhamAI-Backup", "Auto-backup issue: $result")
                    }
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                // Back in the foreground — cancel pending backup & keep-alive
                backupJob?.cancel()
                backupJob = null
                promoteJob?.cancel()
                promoteJob = null
                AhamAIService.stop(appContext)
            }
        }
    }
}