package com.ahamai.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A process-wide coroutine scope used for long-running AI work (chat streaming and
 * agent runs) that must keep going even after the user navigates to another screen
 * or minimizes the app.
 *
 * Unlike a Composable's rememberCoroutineScope (which is cancelled the moment the
 * screen leaves the composition), work launched here is tied to the application
 * process. Combined with the keep-alive foreground service, this lets a response
 * finish (and get saved to history) in the background. Only a hard process kill by
 * the OS ends it.
 */
object AppScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
