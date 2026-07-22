package com.ahamai.app.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges agent loop ↔ UI for permission prompts without bloating CodeAgentScreen.
 * UI sets [promptState] observer; [authorize] suspends until [resolve].
 */
object AgentPermissionGate {
    data class Pending(
        val request: PermissionRequest,
        val deferred: CompletableDeferred<Boolean> // true = allow
    )

    /** Observable by Compose — non-null while waiting for user. */
    val promptState = AtomicReference<Pending?>(null)

    private val mutex = Mutex()

    /**
     * Returns true if tool may run, false if denied.
     * [alwaysAllow] when true and user picks Always → remember grant.
     */
    suspend fun authorize(
        mode: PermissionMode,
        action: String,
        path: String,
        detail: String,
        projectKey: String
    ): Boolean {
        val verdict = ToolPermission.decide(mode, action, path, projectKey)
        when (verdict) {
            PermissionVerdict.ALLOW -> return true
            PermissionVerdict.DENY -> return false
            PermissionVerdict.ASK -> { /* below */ }
        }
        val risk = ToolPermission.riskOf(action)
        val req = PermissionRequest(
            action = action,
            path = path,
            detail = detail.take(160),
            reason = ToolPermission.reasonForAsk(action, risk),
            risk = risk
        )
        return mutex.withLock {
            val d = CompletableDeferred<Boolean>()
            val pending = Pending(req, d)
            promptState.set(pending)
            try {
                d.await()
            } finally {
                promptState.compareAndSet(pending, null)
            }
        }
    }

    fun resolve(allow: Boolean, always: Boolean = false, projectKey: String = "") {
        val p = promptState.get() ?: return
        if (always && allow && projectKey.isNotBlank()) {
            ToolPermission.remember(projectKey, p.request.action, p.request.path)
        }
        p.deferred.complete(allow)
    }

    fun denyMessage(mode: PermissionMode, action: String, path: String): String =
        ToolPermission.denyMessage(action, path, mode)
}
