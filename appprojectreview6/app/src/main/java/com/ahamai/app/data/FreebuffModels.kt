package com.ahamai.app.data

/**
 * Kotlin port of freebuff2api/models.py
 */
data class FreebuffModel(
    val id: String,
    val agentId: String,
    val ownedBy: String = "freebuff",
    val upstreamModelId: String? = null,
    val sessionModelId: String? = null,
    val parentAgentId: String? = null
) {
    val upstreamId: String get() = upstreamModelId ?: id
    val sessionId: String get() = sessionModelId ?: upstreamId
}

object FreebuffModels {
    private const val CONTEXT_PRUNER_AGENT_ID = "context-pruner"
    private const val GEMINI_THINKER_AGENT_ID = "thinker-with-files-gemini"
    private const val GEMINI_THINKER_PARENT_AGENT_ID = "base2-free-kimi"
    private const val GEMINI_THINKER_PARENT_MODEL_ID = "moonshotai/kimi-k2.6"

    val DEFAULT_MODEL = FreebuffModel("deepseek/deepseek-v4-flash", "base2-free-deepseek-flash")

    private val FREEBUFF_MODELS = listOf(
        DEFAULT_MODEL,
        FreebuffModel("deepseek/deepseek-v4-pro", "base2-free-deepseek"),
        FreebuffModel("moonshotai/kimi-k2.6", "base2-free-kimi"),
        FreebuffModel("minimax/minimax-m2.7", "base2-free"),
        FreebuffModel("minimax/minimax-m3", "base2-free-minimax-m3"),
        FreebuffModel("mimo/mimo-v2.5", "base2-free-mimo"),
        FreebuffModel("mimo/mimo-v2.5-pro", "base2-free-mimo-pro"),
    )

    private val GEMINI_FREE_MODELS = listOf(
        FreebuffModel(
            "google/gemini-2.5-flash-lite", "file-picker",
            ownedBy = "google",
            sessionModelId = DEFAULT_MODEL.id,
            parentAgentId = DEFAULT_MODEL.agentId
        ),
        FreebuffModel(
            "google/gemini-3.1-flash-lite-preview", "file-picker-max",
            ownedBy = "google",
            sessionModelId = DEFAULT_MODEL.id,
            parentAgentId = DEFAULT_MODEL.agentId
        ),
        FreebuffModel(
            "google/gemini-3.1-pro-preview", GEMINI_THINKER_AGENT_ID,
            ownedBy = "google",
            sessionModelId = GEMINI_THINKER_PARENT_MODEL_ID,
            parentAgentId = GEMINI_THINKER_PARENT_AGENT_ID
        ),
    )

    val ALL_MODELS: List<FreebuffModel> = FREEBUFF_MODELS + GEMINI_FREE_MODELS

    fun resolve(modelId: String?): FreebuffModel =
        ALL_MODELS.firstOrNull { it.id == modelId } ?: DEFAULT_MODEL

    /** Return model IDs for the API model listing. */
    fun allModelIds(): List<String> = ALL_MODELS.map { it.id }

    /** Return the context-pruner agent ID. */
    fun contextPrunerAgentId(): String = CONTEXT_PRUNER_AGENT_ID
}

data class FreebuffSessionData(
    val instanceId: String,
    val model: String,
    val expiresAt: String? = null,
    var remainingMs: Int? = null
) {
    val isFresh: Boolean get() = remainingMs == null || remainingMs!! > 60_000
}

data class FreebuffRunData(
    val runId: String,
    val agentId: String,
    val startedAt: String,
    val childRunId: String? = null,
    val chatRunId: String? = null,
    val chatStartedAt: String? = null
) {
    val payloadRunId: String get() = chatRunId ?: runId
}