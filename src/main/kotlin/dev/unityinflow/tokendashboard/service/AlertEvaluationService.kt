package dev.unityinflow.tokendashboard.service

import dev.unityinflow.tokendashboard.db.tables.AlertHistoryTable
import dev.unityinflow.tokendashboard.db.tables.BudgetAlertsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.AlertPeriod
import dev.unityinflow.tokendashboard.webhook.WebhookDispatcher
import dev.unityinflow.tokendashboard.webhook.WebhookPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val logger = KotlinLogging.logger {}

class AlertEvaluationService(
    private val db: Database,
    private val webhookDispatcher: WebhookDispatcher,
    private val anomalyDetector: AnomalyDetector,
    private val evaluationIntervalMs: Long = 60_000L,
) {
    private var evaluationJob: Job? = null

    fun start(scope: CoroutineScope) {
        evaluationJob =
            scope.launch {
                logger.info { "Alert evaluation loop started (interval=${evaluationIntervalMs}ms)" }
                while (isActive) {
                    try {
                        evaluateAlerts()
                    } catch (e: Exception) {
                        logger.error(e) { "Alert evaluation cycle failed" }
                    }
                    delay(evaluationIntervalMs)
                }
            }
    }

    fun stop() {
        evaluationJob?.cancel()
        evaluationJob = null
    }

    suspend fun evaluateAlerts() {
        val alerts =
            transaction(db) {
                BudgetAlertsTable.selectAll()
                    .where { BudgetAlertsTable.enabled eq true }
                    .map { row ->
                        AlertRow(
                            id = row[BudgetAlertsTable.id],
                            name = row[BudgetAlertsTable.name],
                            agentId = row[BudgetAlertsTable.agentId],
                            thresholdMicros = row[BudgetAlertsTable.thresholdMicros],
                            period = AlertPeriod.valueOf(row[BudgetAlertsTable.period]),
                            webhookUrl = row[BudgetAlertsTable.webhookUrl],
                            lastFiredAt = row[BudgetAlertsTable.lastFiredAt],
                        )
                    }
            }

        for (alert in alerts) {
            evaluateAlert(alert)
        }
    }

    private suspend fun evaluateAlert(alert: AlertRow) {
        val periodStart = computePeriodStart(alert.period)
        val currentCost = computeCostSince(periodStart, alert.agentId)

        if (currentCost < alert.thresholdMicros) {
            return
        }

        if (alert.lastFiredAt != null && alert.lastFiredAt >= periodStart) {
            return
        }

        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        logger.info { "Alert '${alert.name}' (${alert.id}) fired: cost=$currentCost >= threshold=${alert.thresholdMicros}" }

        val payload =
            WebhookPayload(
                alertId = alert.id,
                alertName = alert.name,
                agentId = alert.agentId,
                period = alert.period.name,
                thresholdMicros = alert.thresholdMicros,
                currentCostMicros = currentCost,
                firedAt = now,
            )

        val webhookStatus =
            if (alert.webhookUrl != null) {
                val result = webhookDispatcher.dispatch(alert.webhookUrl, payload)
                result.statusCode
            } else {
                null
            }

        transaction(db) {
            AlertHistoryTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[alertId] = alert.id
                it[firedAt] = now
                it[costAtFireMicros] = currentCost
                it[AlertHistoryTable.webhookStatus] = webhookStatus
                it[detailJson] =
                    kotlinx.serialization.json.Json.encodeToString(
                        WebhookPayload.serializer(),
                        payload,
                    )
            }

            BudgetAlertsTable.update({ BudgetAlertsTable.id eq alert.id }) {
                it[lastFiredAt] = now
            }
        }

        // Check for anomaly
        val anomaly = anomalyDetector.detectAnomaly(db, alert.period, alert.agentId)
        if (anomaly != null) {
            logger.warn { "Anomaly detected for alert '${alert.name}': ${anomaly.message}" }
        }
    }

    internal fun computePeriodStart(period: AlertPeriod): String {
        val now = LocalDateTime.now()
        val start =
            when (period) {
                AlertPeriod.DAILY -> now.toLocalDate().atStartOfDay()
                AlertPeriod.WEEKLY ->
                    now.toLocalDate().minusDays(now.dayOfWeek.value.toLong() - 1).atStartOfDay()
                AlertPeriod.MONTHLY -> now.toLocalDate().withDayOfMonth(1).atStartOfDay()
                AlertPeriod.SESSION -> LocalDate.EPOCH.atStartOfDay()
            }
        return start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    internal fun computeCostSince(
        periodStart: String,
        agentId: String?,
    ): Long {
        return transaction(db) {
            val query =
                if (agentId != null) {
                    SessionsTable
                        .select(SessionsTable.totalCostMicros.sum())
                        .where {
                            (SessionsTable.startedAt greaterEq periodStart) and
                                (SessionsTable.agentId eq agentId)
                        }
                } else {
                    SessionsTable
                        .select(SessionsTable.totalCostMicros.sum())
                        .where { SessionsTable.startedAt greaterEq periodStart }
                }

            query.single()[SessionsTable.totalCostMicros.sum()] ?: 0L
        }
    }
}

internal data class AlertRow(
    val id: String,
    val name: String,
    val agentId: String?,
    val thresholdMicros: Long,
    val period: AlertPeriod,
    val webhookUrl: String?,
    val lastFiredAt: String?,
)
