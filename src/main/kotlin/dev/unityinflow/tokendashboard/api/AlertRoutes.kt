package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.db.tables.AlertHistoryTable
import dev.unityinflow.tokendashboard.db.tables.BudgetAlertsTable
import dev.unityinflow.tokendashboard.domain.AlertHistoryEntry
import dev.unityinflow.tokendashboard.domain.AlertPeriod
import dev.unityinflow.tokendashboard.domain.BudgetAlert
import dev.unityinflow.tokendashboard.domain.CreateAlertRequest
import dev.unityinflow.tokendashboard.domain.UpdateAlertRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

fun Route.alertRoutes(db: Database) {
    route("/api/v1/alerts") {
        get {
            val alerts =
                transaction(db) {
                    BudgetAlertsTable.selectAll()
                        .orderBy(BudgetAlertsTable.createdAt, SortOrder.DESC)
                        .map { it.toBudgetAlert() }
                }
            call.respond(alerts)
        }

        post {
            val request = call.receive<CreateAlertRequest>()
            val alert =
                transaction(db) {
                    val id = UUID.randomUUID().toString()
                    val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    BudgetAlertsTable.insert {
                        it[BudgetAlertsTable.id] = id
                        it[name] = request.name
                        it[agentId] = request.agentId
                        it[thresholdMicros] = request.thresholdMicros
                        it[period] = request.period.name
                        it[webhookUrl] = request.webhookUrl
                        it[enabled] = true
                        it[createdAt] = now
                    }
                    BudgetAlertsTable.selectAll()
                        .where { BudgetAlertsTable.id eq id }
                        .single()
                        .toBudgetAlert()
                }
            call.respond(HttpStatusCode.Created, alert)
        }

        patch("/{id}") {
            val alertId = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
            val request = call.receive<UpdateAlertRequest>()

            val updated =
                transaction(db) {
                    val count =
                        BudgetAlertsTable.update({ BudgetAlertsTable.id eq alertId }) {
                            request.name?.let { value -> it[name] = value }
                            request.thresholdMicros?.let { value -> it[thresholdMicros] = value }
                            request.period?.let { value -> it[period] = value.name }
                            request.webhookUrl?.let { value -> it[webhookUrl] = value }
                            request.enabled?.let { value -> it[enabled] = value }
                        }
                    if (count > 0) {
                        BudgetAlertsTable.selectAll()
                            .where { BudgetAlertsTable.id eq alertId }
                            .singleOrNull()
                            ?.toBudgetAlert()
                    } else {
                        null
                    }
                }

            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(updated)
            }
        }

        delete("/{id}") {
            val alertId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val deleted =
                transaction(db) {
                    BudgetAlertsTable.deleteWhere { BudgetAlertsTable.id eq alertId }
                }
            if (deleted > 0) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/history") {
            val alertId = call.request.queryParameters["alertId"]
            val history =
                transaction(db) {
                    val query = AlertHistoryTable.selectAll()
                    if (alertId != null) {
                        query.adjustWhere { AlertHistoryTable.alertId eq alertId }
                    }
                    query
                        .orderBy(AlertHistoryTable.firedAt, SortOrder.DESC)
                        .map { row ->
                            AlertHistoryEntry(
                                id = row[AlertHistoryTable.id],
                                alertId = row[AlertHistoryTable.alertId],
                                firedAt = row[AlertHistoryTable.firedAt],
                                costAtFireMicros = row[AlertHistoryTable.costAtFireMicros],
                                webhookStatus = row[AlertHistoryTable.webhookStatus],
                                detailJson = row[AlertHistoryTable.detailJson],
                            )
                        }
                }
            call.respond(history)
        }
    }
}

private fun ResultRow.toBudgetAlert() =
    BudgetAlert(
        id = this[BudgetAlertsTable.id],
        name = this[BudgetAlertsTable.name],
        agentId = this[BudgetAlertsTable.agentId],
        thresholdMicros = this[BudgetAlertsTable.thresholdMicros],
        period = AlertPeriod.valueOf(this[BudgetAlertsTable.period]),
        webhookUrl = this[BudgetAlertsTable.webhookUrl],
        enabled = this[BudgetAlertsTable.enabled],
        lastFiredAt = this[BudgetAlertsTable.lastFiredAt],
        createdAt = this[BudgetAlertsTable.createdAt],
    )
