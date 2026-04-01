package dev.unityinflow.tokendashboard.db.tables

import org.jetbrains.exposed.sql.Table

object AgentCallsTable : Table("agent_calls") {
    val id = text("id")
    val sessionId = text("session_id").references(SessionsTable.id)
    val modelId = text("model_id")
    val calledAt = text("called_at")
    val inputTokens = long("input_tokens")
    val outputTokens = long("output_tokens")
    val cacheReadTokens = long("cache_read_tokens").default(0)
    val cacheWriteTokens = long("cache_write_tokens").default(0)
    val latencyMs = long("latency_ms")
    val costMicros = long("cost_micros")
    val toolName = text("tool_name").nullable()
    val error = text("error").nullable()

    override val primaryKey = PrimaryKey(id)
}
