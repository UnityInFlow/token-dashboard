package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.AgentCostBreakdown
import dev.unityinflow.tokendashboard.domain.Session
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.agentRoutes(db: Database) {
    route("/api/v1/agents") {
        get {
            val agents =
                transaction(db) {
                    SessionsTable
                        .select(
                            SessionsTable.agentId,
                            SessionsTable.agentName,
                            SessionsTable.totalCostMicros.sum(),
                            SessionsTable.id.count(),
                        )
                        .groupBy(SessionsTable.agentId, SessionsTable.agentName)
                        .orderBy(SessionsTable.totalCostMicros.sum(), SortOrder.DESC)
                        .map { row ->
                            val totalCost = row[SessionsTable.totalCostMicros.sum()] ?: 0L
                            val sessionCount = row[SessionsTable.id.count()]
                            AgentCostBreakdown(
                                agentId = row[SessionsTable.agentId],
                                agentName = row[SessionsTable.agentName],
                                totalCostMicros = totalCost,
                                sessionCount = sessionCount,
                                avgSessionCostMicros = if (sessionCount > 0) totalCost / sessionCount else 0,
                            )
                        }
                }
            call.respond(agents)
        }

        get("/{id}") {
            val agentId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val agent =
                transaction(db) {
                    SessionsTable
                        .select(
                            SessionsTable.agentId,
                            SessionsTable.agentName,
                            SessionsTable.totalCostMicros.sum(),
                            SessionsTable.id.count(),
                        )
                        .where { SessionsTable.agentId eq agentId }
                        .groupBy(SessionsTable.agentId, SessionsTable.agentName)
                        .singleOrNull()
                        ?.let { row ->
                            val totalCost = row[SessionsTable.totalCostMicros.sum()] ?: 0L
                            val sessionCount = row[SessionsTable.id.count()]
                            AgentCostBreakdown(
                                agentId = row[SessionsTable.agentId],
                                agentName = row[SessionsTable.agentName],
                                totalCostMicros = totalCost,
                                sessionCount = sessionCount,
                                avgSessionCostMicros = if (sessionCount > 0) totalCost / sessionCount else 0,
                            )
                        }
                }
            if (agent == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(agent)
            }
        }

        get("/{id}/sessions") {
            val agentId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val sessions =
                transaction(db) {
                    SessionsTable.selectAll()
                        .where { SessionsTable.agentId eq agentId }
                        .orderBy(SessionsTable.startedAt, SortOrder.DESC)
                        .map { row ->
                            Session(
                                id = row[SessionsTable.id],
                                agentId = row[SessionsTable.agentId],
                                agentName = row[SessionsTable.agentName],
                                startedAt = row[SessionsTable.startedAt],
                                endedAt = row[SessionsTable.endedAt],
                                totalInputTokens = row[SessionsTable.totalInputTokens],
                                totalOutputTokens = row[SessionsTable.totalOutputTokens],
                                totalCostMicros = row[SessionsTable.totalCostMicros],
                                metadataJson = row[SessionsTable.metadataJson],
                            )
                        }
                }
            call.respond(sessions)
        }
    }
}
