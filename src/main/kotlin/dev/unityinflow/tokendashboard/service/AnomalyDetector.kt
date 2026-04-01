package dev.unityinflow.tokendashboard.service

import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.AlertPeriod
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

data class AnomalyResult(
    val currentCostMicros: Long,
    val averageCostMicros: Long,
    val multiplier: Double,
    val threshold: Double,
    val message: String,
)

class AnomalyDetector(
    private val multiplierThreshold: Double = 2.0,
) {
    fun detectAnomaly(
        db: Database,
        period: AlertPeriod,
        agentId: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): AnomalyResult? {
        val currentPeriodStart = computePeriodStartAt(period, now)
        val previousPeriodStarts = computePreviousPeriodStarts(period, now, count = 4)

        if (previousPeriodStarts.isEmpty()) {
            return null
        }

        val currentCost = queryCostBetween(db, currentPeriodStart, now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), agentId)
        val previousCosts =
            previousPeriodStarts.zipWithNext().map { (start, end) ->
                queryCostBetween(db, start, end, agentId)
            }

        if (previousCosts.isEmpty()) {
            return null
        }

        val avgPreviousCost = previousCosts.sum() / previousCosts.size

        if (avgPreviousCost <= 0) {
            return null
        }

        val actualMultiplier = currentCost.toDouble() / avgPreviousCost.toDouble()

        return if (actualMultiplier >= multiplierThreshold) {
            val result =
                AnomalyResult(
                    currentCostMicros = currentCost,
                    averageCostMicros = avgPreviousCost,
                    multiplier = actualMultiplier,
                    threshold = multiplierThreshold,
                    message =
                        "Current period cost ($currentCost micros) is ${String.format("%.1f", actualMultiplier)}x " +
                            "the rolling average ($avgPreviousCost micros), exceeding ${multiplierThreshold}x threshold",
                )
            logger.warn { result.message }
            result
        } else {
            null
        }
    }

    internal fun computePeriodStartAt(
        period: AlertPeriod,
        at: LocalDateTime,
    ): String {
        val start =
            when (period) {
                AlertPeriod.DAILY -> at.toLocalDate().atStartOfDay()
                AlertPeriod.WEEKLY ->
                    at.toLocalDate().minusDays(at.dayOfWeek.value.toLong() - 1).atStartOfDay()
                AlertPeriod.MONTHLY -> at.toLocalDate().withDayOfMonth(1).atStartOfDay()
                AlertPeriod.SESSION -> return at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            }
        return start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    internal fun computePreviousPeriodStarts(
        period: AlertPeriod,
        now: LocalDateTime,
        count: Int,
    ): List<String> {
        if (period == AlertPeriod.SESSION) {
            return emptyList()
        }

        val starts = mutableListOf<String>()
        for (i in count downTo 0) {
            val shifted =
                when (period) {
                    AlertPeriod.DAILY -> now.minusDays(i.toLong()).toLocalDate().atStartOfDay()
                    AlertPeriod.WEEKLY ->
                        now.toLocalDate()
                            .minusDays(now.dayOfWeek.value.toLong() - 1)
                            .minusWeeks(i.toLong())
                            .atStartOfDay()
                    AlertPeriod.MONTHLY ->
                        now.toLocalDate()
                            .withDayOfMonth(1)
                            .minusMonths(i.toLong())
                            .atStartOfDay()
                    AlertPeriod.SESSION -> return emptyList()
                }
            starts.add(shifted.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        }
        return starts
    }

    private fun queryCostBetween(
        db: Database,
        from: String,
        to: String,
        agentId: String?,
    ): Long {
        return transaction(db) {
            val query =
                if (agentId != null) {
                    SessionsTable
                        .select(SessionsTable.totalCostMicros.sum())
                        .where {
                            (SessionsTable.startedAt greaterEq from) and
                                (SessionsTable.startedAt less to) and
                                (SessionsTable.agentId eq agentId)
                        }
                } else {
                    SessionsTable
                        .select(SessionsTable.totalCostMicros.sum())
                        .where {
                            (SessionsTable.startedAt greaterEq from) and
                                (SessionsTable.startedAt less to)
                        }
                }
            query.single()[SessionsTable.totalCostMicros.sum()] ?: 0L
        }
    }
}
