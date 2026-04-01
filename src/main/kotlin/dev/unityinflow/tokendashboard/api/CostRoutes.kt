package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.AgentCostBreakdown
import dev.unityinflow.tokendashboard.domain.CostSummary
import dev.unityinflow.tokendashboard.domain.CostTimeseriesPoint
import dev.unityinflow.tokendashboard.domain.ModelCostBreakdown
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Route.costRoutes(db: Database) {
    route("/api/v1/costs") {
        get("/summary") {
            val now = LocalDateTime.now()
            val todayStr = now.toLocalDate().atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val weekStartStr =
                now.toLocalDate().minusDays(now.dayOfWeek.value.toLong() - 1)
                    .atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val monthStartStr =
                now.toLocalDate().withDayOfMonth(1)
                    .atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            val summary =
                transaction(db) {
                    val todayCost =
                        SessionsTable
                            .select(SessionsTable.totalCostMicros.sum())
                            .where { SessionsTable.startedAt greaterEq todayStr }
                            .single()[SessionsTable.totalCostMicros.sum()] ?: 0L

                    val weekCost =
                        SessionsTable
                            .select(SessionsTable.totalCostMicros.sum())
                            .where { SessionsTable.startedAt greaterEq weekStartStr }
                            .single()[SessionsTable.totalCostMicros.sum()] ?: 0L

                    val monthCost =
                        SessionsTable
                            .select(SessionsTable.totalCostMicros.sum())
                            .where { SessionsTable.startedAt greaterEq monthStartStr }
                            .single()[SessionsTable.totalCostMicros.sum()] ?: 0L

                    val totalSessions = SessionsTable.selectAll().count()
                    val activeSessions =
                        SessionsTable.selectAll()
                            .where { SessionsTable.endedAt.isNull() }
                            .count()

                    CostSummary(
                        todayMicros = todayCost,
                        thisWeekMicros = weekCost,
                        thisMonthMicros = monthCost,
                        totalSessions = totalSessions,
                        activeSessions = activeSessions,
                    )
                }
            call.respond(summary)
        }

        get("/by-model") {
            val breakdown =
                transaction(db) {
                    AgentCallsTable
                        .select(
                            AgentCallsTable.modelId,
                            AgentCallsTable.costMicros.sum(),
                            AgentCallsTable.inputTokens.sum(),
                            AgentCallsTable.outputTokens.sum(),
                            AgentCallsTable.id.count(),
                        )
                        .groupBy(AgentCallsTable.modelId)
                        .orderBy(AgentCallsTable.costMicros.sum(), SortOrder.DESC)
                        .map { row ->
                            ModelCostBreakdown(
                                modelId = row[AgentCallsTable.modelId],
                                totalCostMicros = row[AgentCallsTable.costMicros.sum()] ?: 0L,
                                totalInputTokens = row[AgentCallsTable.inputTokens.sum()] ?: 0L,
                                totalOutputTokens = row[AgentCallsTable.outputTokens.sum()] ?: 0L,
                                callCount = row[AgentCallsTable.id.count()],
                            )
                        }
                }
            call.respond(breakdown)
        }

        get("/by-agent") {
            val breakdown =
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
            call.respond(breakdown)
        }

        get("/timeseries") {
            val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7

            val points =
                transaction(db) {
                    val fromDate =
                        LocalDate.now().minusDays(days.toLong())
                            .atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                    val results = mutableListOf<CostTimeseriesPoint>()
                    val stmt =
                        connection.prepareStatement(
                            """
                            SELECT date(started_at) as day,
                                   SUM(total_cost_micros) as cost,
                                   COUNT(*) as cnt
                            FROM sessions
                            WHERE started_at >= ?
                            GROUP BY date(started_at)
                            ORDER BY day ASC
                            """.trimIndent(),
                            false,
                        )
                    stmt.fillParameters(
                        listOf(Pair(org.jetbrains.exposed.sql.VarCharColumnType(), fromDate)),
                    )
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        results.add(
                            CostTimeseriesPoint(
                                timestamp = rs.getString(1)!!,
                                costMicros = rs.getLong(2),
                                callCount = rs.getLong(3),
                            ),
                        )
                    }
                    results
                }
            call.respond(points)
        }
    }
}
