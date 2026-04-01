package dev.unityinflow.tokendashboard.db.tables

import org.jetbrains.exposed.sql.Table

object AlertHistoryTable : Table("alert_history") {
    val id = text("id")
    val alertId = text("alert_id").references(BudgetAlertsTable.id)
    val firedAt = text("fired_at")
    val costAtFireMicros = long("cost_at_fire_micros")
    val webhookStatus = integer("webhook_status").nullable()
    val detailJson = text("detail_json").nullable()

    override val primaryKey = PrimaryKey(id)
}
