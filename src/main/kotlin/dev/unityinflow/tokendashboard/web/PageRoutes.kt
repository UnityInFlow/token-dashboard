package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.BudgetAlertsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.AgentCostBreakdown
import dev.unityinflow.tokendashboard.domain.AlertPeriod
import dev.unityinflow.tokendashboard.domain.BudgetAlert
import dev.unityinflow.tokendashboard.domain.CostSummary
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Route.pageRoutes(db: Database) {
    get("/") {
        val now = LocalDateTime.now()
        val todayStr = now.toLocalDate().atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val weekStartStr =
            now.toLocalDate().minusDays(now.dayOfWeek.value.toLong() - 1)
                .atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val monthStartStr =
            now.toLocalDate().withDayOfMonth(1)
                .atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val (summary, topAgents, recentAlerts) =
            transaction(db) {
                val todayCost =
                    SessionsTable.select(SessionsTable.totalCostMicros.sum())
                        .where { SessionsTable.startedAt greaterEq todayStr }
                        .single()[SessionsTable.totalCostMicros.sum()] ?: 0L
                val weekCost =
                    SessionsTable.select(SessionsTable.totalCostMicros.sum())
                        .where { SessionsTable.startedAt greaterEq weekStartStr }
                        .single()[SessionsTable.totalCostMicros.sum()] ?: 0L
                val monthCost =
                    SessionsTable.select(SessionsTable.totalCostMicros.sum())
                        .where { SessionsTable.startedAt greaterEq monthStartStr }
                        .single()[SessionsTable.totalCostMicros.sum()] ?: 0L
                val totalSessions = SessionsTable.selectAll().count()
                val activeSessions =
                    SessionsTable.selectAll()
                        .where { SessionsTable.endedAt.isNull() }
                        .count()

                val costSummary =
                    CostSummary(
                        todayMicros = todayCost,
                        thisWeekMicros = weekCost,
                        thisMonthMicros = monthCost,
                        totalSessions = totalSessions,
                        activeSessions = activeSessions,
                    )

                val agents = queryTopAgents(db)
                val alerts = queryAlerts(db)

                Triple(costSummary, agents, alerts)
            }

        call.respondHtml {
            layout("Home", "dashboard") {
                dashboardContent(summary, topAgents, recentAlerts)
            }
        }
    }

    get("/sessions") {
        val sessions =
            transaction(db) {
                SessionsTable.selectAll()
                    .orderBy(SessionsTable.startedAt, SortOrder.DESC)
                    .limit(50)
                    .map { it.toSession() }
            }

        call.respondHtml {
            layout("Sessions", "sessions") {
                sessionsContent(sessions)
            }
        }
    }

    get("/sessions/{id}") {
        val sessionId =
            call.parameters["id"]
                ?: return@get call.respondText("Missing ID", status = HttpStatusCode.BadRequest)

        val (session, calls) =
            transaction(db) {
                val session =
                    SessionsTable.selectAll()
                        .where { SessionsTable.id eq sessionId }
                        .singleOrNull()
                        ?.toSession()

                val calls =
                    if (session != null) {
                        AgentCallsTable.selectAll()
                            .where { AgentCallsTable.sessionId eq sessionId }
                            .orderBy(AgentCallsTable.calledAt, SortOrder.DESC)
                            .map { it.toAgentCall() }
                    } else {
                        emptyList()
                    }

                Pair(session, calls)
            }

        if (session == null) {
            call.respondText("Session not found", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respondHtml {
            layout("Session Detail", "sessions") {
                sessionDetailContent(session, calls)
            }
        }
    }

    get("/agents") {
        val agents =
            transaction(db) {
                queryTopAgents(db)
            }

        call.respondHtml {
            layout("Agents", "agents") {
                agentsContent(agents)
            }
        }
    }

    get("/agents/{id}") {
        val agentId =
            call.parameters["id"]
                ?: return@get call.respondText("Missing ID", status = HttpStatusCode.BadRequest)

        val (agent, sessions) =
            transaction(db) {
                val agent =
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

                val sessions =
                    if (agent != null) {
                        SessionsTable.selectAll()
                            .where { SessionsTable.agentId eq agentId }
                            .orderBy(SessionsTable.startedAt, SortOrder.DESC)
                            .map { it.toSession() }
                    } else {
                        emptyList()
                    }

                Pair(agent, sessions)
            }

        if (agent == null) {
            call.respondText("Agent not found", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respondHtml {
            layout("Agent Detail", "agents") {
                agentDetailContent(agent, sessions)
            }
        }
    }

    get("/alerts") {
        val alerts =
            transaction(db) {
                queryAlerts(db)
            }

        call.respondHtml {
            layout("Alerts", "alerts") {
                alertsContent(alerts)
            }
        }
    }
}

private fun queryTopAgents(db: Database): List<AgentCostBreakdown> =
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

private fun queryAlerts(db: Database): List<BudgetAlert> =
    BudgetAlertsTable.selectAll()
        .orderBy(BudgetAlertsTable.createdAt, SortOrder.DESC)
        .map { row ->
            BudgetAlert(
                id = row[BudgetAlertsTable.id],
                name = row[BudgetAlertsTable.name],
                agentId = row[BudgetAlertsTable.agentId],
                thresholdMicros = row[BudgetAlertsTable.thresholdMicros],
                period = AlertPeriod.valueOf(row[BudgetAlertsTable.period]),
                webhookUrl = row[BudgetAlertsTable.webhookUrl],
                enabled = row[BudgetAlertsTable.enabled],
                lastFiredAt = row[BudgetAlertsTable.lastFiredAt],
                createdAt = row[BudgetAlertsTable.createdAt],
            )
        }
