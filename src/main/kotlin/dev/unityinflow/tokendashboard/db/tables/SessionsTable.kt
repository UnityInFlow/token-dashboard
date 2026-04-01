package dev.unityinflow.tokendashboard.db.tables

import org.jetbrains.exposed.sql.Table

object SessionsTable : Table("sessions") {
    val id = text("id")
    val agentId = text("agent_id")
    val agentName = text("agent_name")
    val startedAt = text("started_at")
    val endedAt = text("ended_at").nullable()
    val totalInputTokens = long("total_input_tokens").default(0)
    val totalOutputTokens = long("total_output_tokens").default(0)
    val totalCostMicros = long("total_cost_micros").default(0)
    val metadataJson = text("metadata_json").nullable()

    override val primaryKey = PrimaryKey(id)
}
