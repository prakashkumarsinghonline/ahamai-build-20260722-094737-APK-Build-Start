package com.ahamai.app.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tracks daily API usage per user: counts chat, agent, search, image calls,
 * plus estimated token usage. Stores locally in SharedPreferences (offline-safe)
 * and syncs to Firestore for the admin dashboard.
 */
object UsageTracker {

    data class DailyUsage(
        val date: String,
        val chat: Int = 0,
        val agent: Int = 0,
        val search: Int = 0,
        val image: Int = 0,
        val e2bMinutes: Int = 0,
        val totalTokens: Int = 0
    ) {
        val total get() = chat + agent + search + image
    }

    private const val PREFS_PREFIX = "usage_"

    private fun getToday(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Record one API call of the given type. */
    fun recordCall(context: Context, type: String, tokens: Int = 0) {
        val today = getToday()
        val prefs = context.getSharedPreferences(PREFS_PREFIX + today, Context.MODE_PRIVATE)
        val count = prefs.getInt(type, 0)
        prefs.edit().putInt(type, count + 1).apply()
        if (tokens > 0) {
            val total = prefs.getInt("tokens", 0)
            prefs.edit().putInt("tokens", total + tokens).apply()
        }
    }

    /** Get today's local usage totals. */
    fun getTodayUsage(context: Context): DailyUsage {
        val today = getToday()
        val prefs = context.getSharedPreferences(PREFS_PREFIX + today, Context.MODE_PRIVATE)
        return DailyUsage(
            date = today,
            chat = prefs.getInt("chat", 0),
            agent = prefs.getInt("agent", 0),
            search = prefs.getInt("search", 0),
            image = prefs.getInt("image", 0),
            e2bMinutes = prefs.getInt("e2b", 0),
            totalTokens = prefs.getInt("tokens", 0)
        )
    }

    /** Sync local usage to Firestore for admin panel. */
    suspend fun syncToFirestore(context: Context) = withContext(Dispatchers.IO) {
        val uid = AuthManager.uid() ?: return@withContext
        try {
            val today = getToday()
            val prefs = context.getSharedPreferences(PREFS_PREFIX + today, Context.MODE_PRIVATE)
            val data = hashMapOf(
                "date" to today,
                "chat" to prefs.getInt("chat", 0),
                "agent" to prefs.getInt("agent", 0),
                "search" to prefs.getInt("search", 0),
                "image" to prefs.getInt("image", 0),
                "e2b" to prefs.getInt("e2b", 0),
                "tokens" to prefs.getInt("tokens", 0)
            )
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("usage").document(today)
                .set(data)
        } catch (_: Exception) {}
    }

    // --- Admin helpers: aggregate stats across all users ---

    /** Fetch all users' usage for today from Firestore. Returns list of (email, usage). */
    suspend fun fetchAllUsage(context: Context): List<Pair<String, DailyUsage>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, DailyUsage>>()
        try {
            val today = getToday()
            val usersSnap = FirebaseFirestore.getInstance()
                .collection("users")
                .get()
                .await()
            for (userDoc in usersSnap.documents) {
                val uid = userDoc.id
                val email = userDoc.getString("email") ?: uid
                val usageDoc = FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("usage").document(today)
                    .get()
                    .await()
                if (usageDoc.exists()) {
                    results.add(email to DailyUsage(
                        date = today,
                        chat = (usageDoc.getLong("chat") ?: 0).toInt(),
                        agent = (usageDoc.getLong("agent") ?: 0).toInt(),
                        search = (usageDoc.getLong("search") ?: 0).toInt(),
                        image = (usageDoc.getLong("image") ?: 0).toInt(),
                        e2bMinutes = (usageDoc.getLong("e2b") ?: 0).toInt(),
                        totalTokens = (usageDoc.getLong("tokens") ?: 0).toInt()
                    ))
                } else {
                    results.add(email to DailyUsage(date = today))
                }
            }
        } catch (_: Exception) {}
        results
    }

    // ============================================================================================
    // MONTHLY token + feature-cap tracking (billing). Stored per calendar month, offline-safe.
    // ============================================================================================

    private fun monthKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    private fun monthPrefs(context: Context) =
        context.getSharedPreferences("usage_month_" + monthKey(), Context.MODE_PRIVATE)

    /** ~4 chars per token — a universal estimate that works across every provider. */
    fun estimateTokens(chars: Int): Long = (chars / 4L).coerceAtLeast(0L)

    /** Record token usage for this month. */
    fun recordTokens(context: Context, tokens: Long) {
        if (tokens <= 0) return
        val p = monthPrefs(context)
        p.edit().putLong("tokens", p.getLong("tokens", 0L) + tokens).apply()
        // Mirror into today's bucket (admin dashboard reads daily tokens).
        recordCall(context, "_tok_noop", 0)
        val today = getToday()
        val dp = context.getSharedPreferences(PREFS_PREFIX + today, Context.MODE_PRIVATE)
        dp.edit().putInt("tokens", (dp.getInt("tokens", 0) + tokens.toInt())).apply()
    }

    /** Convenience: estimate + record from prompt/response character counts. */
    fun recordChatTokens(context: Context, promptChars: Int, responseChars: Int) {
        recordTokens(context, estimateTokens(promptChars + responseChars))
    }

    fun getMonthlyTokens(context: Context): Long = monthPrefs(context).getLong("tokens", 0L)

    /** Increment a monthly feature counter (image / browser / apk / video / scan). */
    fun recordFeature(context: Context, key: String) {
        val p = monthPrefs(context)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    fun getFeature(context: Context, key: String): Int = monthPrefs(context).getInt(key, 0)

    // ── Plan-aware helpers ──────────────────────────────────────────────────────────────────
    fun currentPlan(context: Context): Plans.Plan = Plans.byId(PreferencesManager(context).getPlanId())

    fun tokensRemaining(context: Context): Long =
        (currentPlan(context).tokens - getMonthlyTokens(context)).coerceAtLeast(0L)

    /** 0f..1f fraction of the monthly token allowance already used. */
    fun tokenFraction(context: Context): Float {
        val limit = currentPlan(context).tokens.coerceAtLeast(1L)
        return (getMonthlyTokens(context).toFloat() / limit).coerceIn(0f, 1f)
    }

    /** Whether the user is still under the monthly cap for a feature. UNLIMITED (-1) ⇒ always true. */
    fun canUseFeature(context: Context, key: String): Boolean {
        val plan = currentPlan(context)
        val cap = when (key) {
            "image" -> plan.images
            "browser" -> plan.browser
            "apk" -> plan.apkBuilds
            "video" -> plan.videos
            "scan" -> plan.scans
            else -> Plans.UNLIMITED
        }
        if (cap == Plans.UNLIMITED) return true
        return getFeature(context, key) < cap
    }

    // ── Activity history (chat + agent counts) for the Usage screen chart ────────────────────
    // Reads the existing per-day SharedPreferences buckets, so it's fully offline and needs no
    // new writes beyond the chat/agent recordCall()s already made on each run.

    data class DayActivity(val label: String, val chat: Int, val agent: Int) {
        val total get() = chat + agent
    }

    /** Chat + agent counts for the last [days] days, oldest first, each labelled by weekday. */
    fun getRecentActivity(context: Context, days: Int = 7): List<DayActivity> {
        val out = ArrayList<DayActivity>(days)
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFmt = SimpleDateFormat("EEE", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1))
        repeat(days) {
            val date = cal.time
            val p = context.getSharedPreferences(PREFS_PREFIX + keyFmt.format(date), Context.MODE_PRIVATE)
            out.add(DayActivity(labelFmt.format(date), p.getInt("chat", 0), p.getInt("agent", 0)))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    /** Sum of this calendar month's chat and agent counts, as (chat, agent). */
    fun getMonthlyActivity(context: Context): Pair<Int, Int> {
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        var chat = 0; var agent = 0
        repeat(dayOfMonth) {
            val p = context.getSharedPreferences(PREFS_PREFIX + keyFmt.format(c.time), Context.MODE_PRIVATE)
            chat += p.getInt("chat", 0); agent += p.getInt("agent", 0)
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return chat to agent
    }

    /** Days remaining until the monthly allowance resets (1st of next month). */
    fun daysUntilReset(): Int {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (max - today + 1).coerceAtLeast(1)
    }
}