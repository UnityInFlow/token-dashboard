package dev.unityinflow.tokendashboard.db.tables

import org.jetbrains.exposed.sql.Table

object BudgetAlertsTable : Table("budget_alerts") {
    val id = text("id")
    val name = text("name")
    val agentId = text("agent_id").nullable()
    val thresholdMicros = long("threshold_micros")
    val period = text("period")
    val webhookUrl = text("webhook_url").nullable()
    val enabled = bool("enabled").default(true)
    val lastFiredAt = text("last_fired_at").nullable()
    val createdAt = text("created_at")

    override val primaryKey = PrimaryKey(id)
}
