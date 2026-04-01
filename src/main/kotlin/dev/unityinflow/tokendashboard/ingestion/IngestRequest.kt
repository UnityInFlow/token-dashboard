package dev.unityinflow.tokendashboard.ingestion

import kotlinx.serialization.Serializable

@Serializable
data class IngestRequest(
    val records: List<IngestRecord>,
)

@Serializable
data class IngestRecord(
    val sessionId: String,
    val agentId: String,
    val agentName: String,
    val modelId: String,
    val calledAt: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val latencyMs: Long,
    val toolName: String? = null,
    val error: String? = null,
)

@Serializable
data class IngestResponse(
    val accepted: Int,
    val errors: List<String> = emptyList(),
)
