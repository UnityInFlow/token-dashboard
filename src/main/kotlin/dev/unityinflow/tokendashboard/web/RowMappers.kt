package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.AgentCall
import dev.unityinflow.tokendashboard.domain.Session
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toSession() =
    Session(
        id = this[SessionsTable.id],
        agentId = this[SessionsTable.agentId],
        agentName = this[SessionsTable.agentName],
        startedAt = this[SessionsTable.startedAt],
        endedAt = this[SessionsTable.endedAt],
        totalInputTokens = this[SessionsTable.totalInputTokens],
        totalOutputTokens = this[SessionsTable.totalOutputTokens],
        totalCostMicros = this[SessionsTable.totalCostMicros],
        metadataJson = this[SessionsTable.metadataJson],
    )

fun ResultRow.toAgentCall() =
    AgentCall(
        id = this[AgentCallsTable.id],
        sessionId = this[AgentCallsTable.sessionId],
        modelId = this[AgentCallsTable.modelId],
        calledAt = this[AgentCallsTable.calledAt],
        inputTokens = this[AgentCallsTable.inputTokens],
        outputTokens = this[AgentCallsTable.outputTokens],
        cacheReadTokens = this[AgentCallsTable.cacheReadTokens],
        cacheWriteTokens = this[AgentCallsTable.cacheWriteTokens],
        latencyMs = this[AgentCallsTable.latencyMs],
        costMicros = this[AgentCallsTable.costMicros],
        toolName = this[AgentCallsTable.toolName],
        error = this[AgentCallsTable.error],
    )
