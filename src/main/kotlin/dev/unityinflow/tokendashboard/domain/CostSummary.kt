package dev.unityinflow.tokendashboard.domain

import kotlinx.serialization.Serializable

@Serializable
data class CostSummary(
    val todayMicros: Long,
    val thisWeekMicros: Long,
    val thisMonthMicros: Long,
    val totalSessions: Long,
    val activeSessions: Long,
)

@Serializable
data class ModelCostBreakdown(
    val modelId: String,
    val totalCostMicros: Long,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val callCount: Long,
)

@Serializable
data class AgentCostBreakdown(
    val agentId: String,
    val agentName: String,
    val totalCostMicros: Long,
    val sessionCount: Long,
    val avgSessionCostMicros: Long,
)

@Serializable
data class CostTimeseriesPoint(
    val timestamp: String,
    val costMicros: Long,
    val callCount: Long,
)

@Serializable
data class BurnRate(
    val tokensPerMinute: Double,
    val projectedSessionCostMicros: Long,
    val activeSessions: Long,
    val windowMinutes: Int,
)
