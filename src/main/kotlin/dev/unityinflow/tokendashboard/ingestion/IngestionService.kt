package dev.unityinflow.tokendashboard.ingestion

import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val logger = KotlinLogging.logger {}

class IngestionService(
    private val db: Database,
) {
    fun ingest(records: List<IngestRecord>): IngestResponse {
        val errors = mutableListOf<String>()
        var accepted = 0

        for (record in records) {
            try {
                processRecord(record)
                accepted++
            } catch (e: Exception) {
                logger.warn(e) { "Failed to ingest record for session ${record.sessionId}" }
                errors.add("Record ${record.sessionId}/${record.calledAt}: ${e.message}")
            }
        }

        logger.info { "Ingested $accepted/${records.size} records" }
        return IngestResponse(accepted = accepted, errors = errors)
    }

    private fun processRecord(record: IngestRecord) {
        val costMicros = CostCalculator.calculateCostMicros(record, db)
        val callId = UUID.randomUUID().toString()

        transaction(db) {
            // Ensure session exists (upsert-like: create if missing)
            val sessionExists =
                SessionsTable.selectAll()
                    .where { SessionsTable.id eq record.sessionId }
                    .count() > 0

            if (!sessionExists) {
                val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                SessionsTable.insert {
                    it[id] = record.sessionId
                    it[agentId] = record.agentId
                    it[agentName] = record.agentName
                    it[startedAt] = record.calledAt.ifBlank { now }
                    it[totalInputTokens] = record.inputTokens
                    it[totalOutputTokens] = record.outputTokens
                    it[totalCostMicros] = costMicros
                }
            } else {
                SessionsTable.update({ SessionsTable.id eq record.sessionId }) {
                    with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                        it[totalInputTokens] = totalInputTokens + record.inputTokens
                        it[totalOutputTokens] = totalOutputTokens + record.outputTokens
                        it[totalCostMicros] = totalCostMicros + costMicros
                    }
                }
            }

            // Insert the call record
            AgentCallsTable.insert {
                it[id] = callId
                it[sessionId] = record.sessionId
                it[modelId] = record.modelId
                it[calledAt] = record.calledAt
                it[inputTokens] = record.inputTokens
                it[outputTokens] = record.outputTokens
                it[cacheReadTokens] = record.cacheReadTokens
                it[cacheWriteTokens] = record.cacheWriteTokens
                it[latencyMs] = record.latencyMs
                it[AgentCallsTable.costMicros] = costMicros
                it[toolName] = record.toolName
                it[error] = record.error
            }
        }
    }
}
