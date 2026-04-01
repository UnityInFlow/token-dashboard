package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.Session
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.sessionRoutes(db: Database) {
    route("/api/v1/sessions") {
        get {
            val agentId = call.request.queryParameters["agentId"]
            val from = call.request.queryParameters["from"]
            val to = call.request.queryParameters["to"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20).coerceIn(1, 100)

            val sessions =
                transaction(db) {
                    var condition: Op<Boolean> = Op.TRUE
                    if (agentId != null) {
                        condition = condition and (SessionsTable.agentId eq agentId)
                    }
                    if (from != null) {
                        condition = condition and (SessionsTable.startedAt greaterEq from)
                    }
                    if (to != null) {
                        condition = condition and (SessionsTable.startedAt lessEq to)
                    }

                    SessionsTable.selectAll()
                        .where(condition)
                        .orderBy(SessionsTable.startedAt, SortOrder.DESC)
                        .limit(pageSize)
                        .offset(((page - 1) * pageSize).toLong())
                        .map { it.toSession() }
                }

            call.respond(sessions)
        }

        get("/active") {
            val sessions =
                transaction(db) {
                    SessionsTable.selectAll()
                        .where { SessionsTable.endedAt.isNull() }
                        .orderBy(SessionsTable.startedAt, SortOrder.DESC)
                        .map { it.toSession() }
                }
            call.respond(sessions)
        }

        get("/{id}") {
            val sessionId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val session =
                transaction(db) {
                    SessionsTable.selectAll()
                        .where { SessionsTable.id eq sessionId }
                        .singleOrNull()
                        ?.toSession()
                }
            if (session == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(session)
            }
        }
    }
}

private fun ResultRow.toSession() =
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
