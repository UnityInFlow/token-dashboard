package dev.unityinflow.tokendashboard.domain

import kotlinx.serialization.Serializable

@Serializable
data class AgentCall(
    val id: String,
    val sessionId: String,
    val modelId: String,
    val calledAt: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val latencyMs: Long,
    val costMicros: Long,
    val toolName: String? = null,
    val error: String? = null,
)
