package dev.unityinflow.tokendashboard.domain

import kotlinx.serialization.Serializable

@Serializable
data class BudgetAlert(
    val id: String,
    val name: String,
    val agentId: String? = null,
    val thresholdMicros: Long,
    val period: AlertPeriod,
    val webhookUrl: String? = null,
    val enabled: Boolean = true,
    val lastFiredAt: String? = null,
    val createdAt: String,
)

@Serializable
enum class AlertPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    SESSION,
}

@Serializable
data class AlertHistoryEntry(
    val id: String,
    val alertId: String,
    val firedAt: String,
    val costAtFireMicros: Long,
    val webhookStatus: Int? = null,
    val detailJson: String? = null,
)

@Serializable
data class CreateAlertRequest(
    val name: String,
    val agentId: String? = null,
    val thresholdMicros: Long,
    val period: AlertPeriod,
    val webhookUrl: String? = null,
)

@Serializable
data class UpdateAlertRequest(
    val name: String? = null,
    val thresholdMicros: Long? = null,
    val period: AlertPeriod? = null,
    val webhookUrl: String? = null,
    val enabled: Boolean? = null,
)
