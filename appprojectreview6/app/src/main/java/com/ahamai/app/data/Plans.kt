package com.ahamai.app.data

/**
 * Single source of truth for AhamAI subscription tiers (token-based, hybrid).
 *
 * Tiers: Free · Pro · Plus · Enterprise
 *
 * Billing model:
 *  - Each plan grants a MONTHLY token allowance (input + output tokens).
 *  - Compute-heavy features (image gen, live browser, APK builds, video, scans)
 *    are additionally capped per month.
 *  - Chat mode is unlimited on every plan (fair-use); agent / build work uses the caps.
 *  - When capacity is high, the queue prioritizes paid plans over Free.
 *
 * Prices are stored in BOTH USD and INR; the UI lets the user switch currency.
 */
object Plans {

    /** Use this sentinel for "effectively unlimited" caps. */
    const val UNLIMITED = -1

    data class Plan(
        val id: String,
        val name: String,
        val tagline: String,
        val usd: Int,
        val inr: Int,
        val tokens: Long,          // monthly token allowance
        val images: Int,          // monthly image generations
        val browser: Int,         // monthly live-browser sessions
        val apkBuilds: Int,       // monthly cloud APK builds
        val videos: Int,          // monthly video-short renders
        val scans: Int,           // monthly security scans (0 = locked)
        val concurrent: Int,      // simultaneous agent tasks
        val security: Boolean,    // full security suite unlocked
        val team: Boolean,        // team seats / SSO / audit logs
        val priority: Boolean,    // queue priority when capacity is high
        val highlight: Boolean,   // "most popular" accent in the UI
        val features: List<String>
    ) {
        val isFree get() = usd == 0
    }

    val FREE = Plan(
        id = "free", name = "Free", tagline = "Start building with ahamai",
        usd = 0, inr = 0, tokens = 1_000_000L,
        images = 40, browser = 15, apkBuilds = 2, videos = 0, scans = 0,
        concurrent = 1, security = false, team = false, priority = false, highlight = false,
        features = listOf(
            "Unlimited chat mode — no limits",
            "1M tokens / month",
            "2 APK builds / month",
            "40 image generations",
            "Short agent tasks (1 at a time)",
            "Queue yields to paid when busy"
        )
    )

    val PRO = Plan(
        id = "pro", name = "Pro", tagline = "For everyday builders",
        usd = 5, inr = 449, tokens = 7_000_000L,
        images = 250, browser = 50, apkBuilds = 5, videos = 8, scans = 0,
        concurrent = 2, security = false, team = false, priority = true, highlight = true,
        features = listOf(
            "Unlimited chat mode — no limits",
            "7M tokens / month",
            "5 APK builds / month",
            "Priority queue when capacity is high",
            "250 images • 50 browser sessions",
            "2 tasks at once • GitHub integration"
        )
    )

    val PLUS = Plan(
        id = "plus", name = "Plus", tagline = "For power users & founders",
        usd = 10, inr = 899, tokens = 18_000_000L,
        images = 500, browser = 100, apkBuilds = 10, videos = 20, scans = 5,
        concurrent = 3, security = false, team = false, priority = true, highlight = false,
        features = listOf(
            "Unlimited chat mode — no limits",
            "18M tokens / month  (+11M vs Pro)",
            "10 APK builds / month  (+5 vs Pro)",
            "Priority queue when capacity is high",
            "500 images • 100 browser sessions",
            "3 tasks at once • light security scans"
        )
    )

    val ENTERPRISE = Plan(
        id = "enterprise", name = "Enterprise", tagline = "For teams & serious shipping",
        usd = 20, inr = 1699, tokens = 50_000_000L,
        images = 1500, browser = UNLIMITED, apkBuilds = 50, videos = 40, scans = 50,
        concurrent = 5, security = true, team = true, priority = true, highlight = false,
        features = listOf(
            "Unlimited chat mode — no limits",
            "50M tokens / month",
            "50 APK builds / month",
            "Security testing for web & APK",
            "Full security suite (50 scans / mo)",
            "Priority queue • team seats • support"
        )
    )

    /** Free → Pro → Plus → Enterprise */
    val ALL: List<Plan> = listOf(FREE, PRO, PLUS, ENTERPRISE)

    /**
     * Resolve plan by id. Legacy ids (`starter`, old mappings) fall back safely
     * so existing installs never break.
     */
    fun byId(id: String?): Plan = when (id) {
        "starter" -> PRO          // removed tier → map to Pro
        else -> ALL.firstOrNull { it.id == id } ?: FREE
    }

    /** "$5" / "₹449" — or "Free" for the free tier. */
    fun priceLabel(plan: Plan, inr: Boolean): String = when {
        plan.usd == 0 -> "Free"
        inr -> "₹${plan.inr}"
        else -> "$${plan.usd}"
    }

    /** Pretty token label: 500K / 3M / 20M. */
    fun tokenLabel(tokens: Long): String = when {
        tokens >= 1_000_000L -> {
            val m = tokens / 100_000L / 10.0
            "${m.toString().trimEnd('0').trimEnd('.')}M"
        }
        tokens >= 1_000L -> "${tokens / 1_000L}K"
        else -> tokens.toString()
    }

    fun capLabel(value: Int): String = if (value == UNLIMITED) "Unlimited" else value.toString()
}
