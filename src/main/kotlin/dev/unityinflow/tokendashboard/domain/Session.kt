package dev.unityinflow.tokendashboard.domain

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val agentId: String,
    val agentName: String,
    val startedAt: String,
    val endedAt: String? = null,
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalCostMicros: Long = 0,
    val metadataJson: String? = null,
)
