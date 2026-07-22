package com.ahamai.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Singleton manager for Freebuff/Codebuff native integration.
 *
 * Thread-safe. Lazily creates a FreebuffClient per token, reusing cached instances.
 *
 * USAGE in api_providers.json:
 * {
 *   "id": "freebuff",
 *   "name": "Freebuff (Free Models)",
 *   "baseUrl": "https://www.codebuff.com",
 *   "enabled": true,
 *   "keys": ["your-freebuff-bearer-token"],
 *   "fetchModels": false,
 *   "agentModeDefault": true,
 *   "models": []
 * }
 *
 * The models are declared in FreebuffModels.kt — deepseek, kimi, minimax, gemini etc.
 */
object FreebuffManager {
    private const val TAG = "FreebuffManager"

    /** Cache of FreebuffClient instances keyed by token. */
    private val clients = mutableMapOf<String, FreebuffClient>()
    private val clientMutex = Mutex()

    /** Get or create a FreebuffClient for the given token. */
    private suspend fun getClient(token: String): FreebuffClient = clientMutex.withLock {
        clients.getOrPut(token) {
            Log.d(TAG, "Creating new FreebuffClient for token=${token.take(8)}…")
            // Store the token persistently so it's available across sessions
            FreebuffClient(bearerToken = token)
        }
    }

    /**
     * Non-streaming completion via Freebuff.  Automatically resolves the model
     * ID, acquires a session, and handles all the ads/streak/run lifecycle.
     *
     * ```
     * val result = FreebuffManager.complete("my-token", "deepseek/deepseek-v4-flash", messages)
     * ```
     */
    suspend fun complete(token: String, model: String, messages: List<Pair<String, String>>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val client = getClient(token)
                client.complete(model, messages)
            } catch (e: Exception) {
                Log.e(TAG, "Freebuff complete failed: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Streaming completion via Freebuff.  Returns a Flow<StreamDelta> that the
     * ChatScreen can collect just like any other provider.
     *
     * ```
     * FreebuffManager.stream("my-token", "deepseek/deepseek-v4-flash", messages)
     *     .collect { delta -> ... }
     * ```
     */
    fun stream(token: String, model: String, messages: List<Pair<String, String>>): Flow<StreamDelta> {
        val client = clients[token]
        if (client != null) {
            return client.streamChat(model, messages)
        }
        // Lazily create in the flow
        return kotlinx.coroutines.flow.flow {
            val c = getClient(token)
            emitAll(c.streamChat(model, messages))
        }
    }

    /**
     * Verify that a Freebuff token works by making a simple health check.
     */
    suspend fun pingToken(token: String): String = withContext(Dispatchers.IO) {
        try {
            val client = getClient(token)
            val session = client.ensureSession(FreebuffModels.DEFAULT_MODEL.sessionId)
            if (session != null) {
                "OK"
            } else {
                "Failed to create session"
            }
        } catch (e: Exception) {
            "ERR: ${e.message?.take(40)}"
        }
    }

    /**
     * Clear cached clients (e.g., when the user updates their token).
     */
    fun reset() {
        clients.clear()
    }
}