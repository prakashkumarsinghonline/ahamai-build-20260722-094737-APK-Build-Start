package com.ahamai.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Robust, native implementation of the Freebuff token-acquisition flow used by
 * https://freebuff.llm.pm — the same 2-step protocol the official auth page runs:
 *
 *   1. POST /api/code  → { fingerprintId, fingerprintHash, loginUrl, expiresAt }
 *   2. user opens `loginUrl` and signs in with GitHub / Google (the ONLY way to mint a
 *      Freebuff auth token — there is no anonymous path)
 *   3. POST /api/status { fingerprintId, fingerprintHash, expiresAt } → polled until it
 *      returns { user: { authToken, ... } }
 *
 * This replaces the old, fragile approach of scraping the WebView's localStorage /
 * cookies for a token-shaped string (which fired before login completed and often
 * grabbed the pre-login fingerprint instead of the real token).
 *
 * The token returned here is exactly what [FreebuffClient] needs as its bearer token.
 */
object FreebuffAuth {
    private const val TAG = "FreebuffAuth"
    const val BASE_URL = "https://freebuff.llm.pm"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // Cloudflare in front of freebuff.llm.pm 403s some non-browser UAs (e.g. Python-urllib).
    // okhttp's default passes today, but set an explicit browser UA to stay robust.
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /** Result of step 1 — the login session the user must complete in a browser/WebView. */
    data class LoginSession(
        val fingerprintId: String,
        val fingerprintHash: String,
        val loginUrl: String,
        val expiresAt: Long
    )

    private fun postJson(path: String, body: JSONObject): JSONObject? {
        return try {
            val req = Request.Builder()
                .url("$BASE_URL$path")
                .post(body.toString().toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", UA)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "$path -> HTTP ${resp.code}: ${text.take(200)}")
                    return null
                }
                if (text.isBlank()) null else JSONObject(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "$path failed: ${e.message}")
            null
        }
    }

    /** Step 1: generate a login URL + fingerprint session. */
    suspend fun generateLoginSession(): LoginSession? = withContext(Dispatchers.IO) {
        val data = postJson("/api/code", JSONObject()) ?: return@withContext null
        val loginUrl = data.optString("loginUrl", "")
        val fpId = data.optString("fingerprintId", "")
        val fpHash = data.optString("fingerprintHash", "")
        if (loginUrl.isBlank() || fpId.isBlank() || fpHash.isBlank()) {
            Log.e(TAG, "generateLoginSession: incomplete response $data")
            return@withContext null
        }
        LoginSession(
            fingerprintId = fpId,
            fingerprintHash = fpHash,
            loginUrl = loginUrl,
            expiresAt = data.optLong("expiresAt", 0L)
        )
    }

    /**
     * One status check. Returns:
     *  - non-null authToken  → login completed, token captured
     *  - null                → still pending (or transient error); keep polling
     * Throws nothing; transient failures are treated as "pending".
     */
    suspend fun checkStatus(session: LoginSession): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("fingerprintId", session.fingerprintId)
            put("fingerprintHash", session.fingerprintHash)
            put("expiresAt", session.expiresAt)
        }
        val data = postJson("/api/status", body) ?: return@withContext null
        if (data.optBoolean("pending", false)) return@withContext null
        val user = data.optJSONObject("user") ?: return@withContext null
        user.optString("authToken", "").ifBlank { null }
    }

    /**
     * Poll [checkStatus] until a token appears or [timeoutMs] elapses.
     * Calls [onTick] before each attempt so callers can surface progress.
     * Returns the captured authToken, or null on timeout.
     */
    suspend fun pollForToken(
        session: LoginSession,
        timeoutMs: Long = 180_000L,
        intervalMs: Long = 2_000L,
        onTick: (attempt: Int) -> Unit = {}
    ): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            attempt++
            onTick(attempt)
            val token = checkStatus(session)
            if (!token.isNullOrBlank()) return token
            delay(intervalMs)
        }
        return null
    }
}
