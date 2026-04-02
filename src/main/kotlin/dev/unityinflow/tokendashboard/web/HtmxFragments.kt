package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.db.tables.BudgetAlertsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.AgentCostBreakdown
import dev.unityinflow.tokendashboard.domain.AlertPeriod
import dev.unityinflow.tokendashboard.domain.BudgetAlert
import dev.unityinflow.tokendashboard.domain.BurnRate
import dev.unityinflow.tokendashboard.domain.CostTimeseriesPoint
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.html.body
import kotlinx.html.stream.createHTML
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Route.htmxFragments(db: Database) {
    route("/htmx") {
        get("/sessions-table") {
            val sessions =
                transaction(db) {
                    SessionsTable.selectAll()
                        .orderBy(SessionsTable.startedAt, SortOrder.DESC)
                        .limit(50)
                        .map { it.toSession() }
                }

            val html =
                createHTML().body {
                    sessionsTableContent(sessions)
                }
            call.respondText(html, ContentType.Text.Html)
        }

        get("/agents-grid") {
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

            val html =
                createHTML().body {
                    agentsGridContent(agents)
                }
            call.respondText(html, ContentType.Text.Html)
        }

        get("/alerts-table") {
            val alerts =
                transaction(db) {
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
                }

            val html =
                createHTML().body {
                    alertsTableContent(alerts)
                }
            call.respondText(html, ContentType.Text.Html)
        }

        get("/cost-chart-data") {
            val points =
                transaction(db) {
                    val fromDate =
                        LocalDate.now().minusDays(7)
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
                        listOf(Pair(VarCharColumnType(), fromDate)),
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

            val labels = points.map { it.timestamp }
            val values = points.map { it.costMicros }

            val html =
                createHTML().body {
                    costChartScript(labels, values)
                }
            call.respondText(html, ContentType.Text.Html)
        }

        get("/burn-rate") {
            val windowMinutes = 5

            val burnRate =
                transaction(db) {
                    val cutoffStr =
                        LocalDateTime.now().minusMinutes(windowMinutes.toLong())
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                    val activeSessions =
                        SessionsTable.selectAll()
                            .where { SessionsTable.endedAt.isNull() }
                            .count()

                    val stmt =
                        connection.prepareStatement(
                            """
                            SELECT COALESCE(SUM(input_tokens + output_tokens), 0) as total_tokens,
                                   COALESCE(SUM(cost_micros), 0) as total_cost
                            FROM agent_calls
                            WHERE called_at >= ?
                            """.trimIndent(),
                            false,
                        )
                    stmt.fillParameters(
                        listOf(Pair(VarCharColumnType(), cutoffStr)),
                    )
                    val rs = stmt.executeQuery()
                    rs.next()
                    val totalTokens = rs.getLong(1)
                    val totalCost = rs.getLong(2)

                    val tokensPerMinute = totalTokens.toDouble() / windowMinutes
                    val costPerMinute = totalCost.toDouble() / windowMinutes
                    val projectedHourlyCostMicros = (costPerMinute * 60).toLong()

                    BurnRate(
                        tokensPerMinute = tokensPerMinute,
                        projectedSessionCostMicros = projectedHourlyCostMicros,
                        activeSessions = activeSessions,
                        windowMinutes = windowMinutes,
                    )
                }

            val html =
                createHTML().body {
                    burnRateContent(burnRate)
                }
            call.respondText(html, ContentType.Text.Html)
        }
    }
}
