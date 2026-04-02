package dev.unityinflow.tokendashboard.otlp

import dev.unityinflow.tokendashboard.ingestion.IngestRecord
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.metrics.v1.Metric
import io.opentelemetry.proto.metrics.v1.NumberDataPoint
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

object OtlpMetricMapper {
    private val TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun mapToIngestRecords(request: ExportMetricsServiceRequest): List<IngestRecord> {
        val records = mutableListOf<IngestRecord>()

        for (resourceMetrics in request.resourceMetricsList) {
            val resourceAttrs = resourceMetrics.resource.attributesList.toAttributeMap()
            val agentId = resourceAttrs["agent.id"] ?: resourceAttrs["service.instance.id"] ?: UUID.randomUUID().toString()
            val agentName = resourceAttrs["agent.name"] ?: resourceAttrs["service.name"] ?: "unknown"
            val sessionId = resourceAttrs["session.id"] ?: "otlp-${UUID.randomUUID()}"

            for (scopeMetrics in resourceMetrics.scopeMetricsList) {
                val metricsByTimestamp = groupMetricsByTimestamp(scopeMetrics.metricsList)

                for ((timestamp, metricGroup) in metricsByTimestamp) {
                    val record = buildIngestRecord(
                        metricGroup = metricGroup,
                        agentId = agentId,
                        agentName = agentName,
                        sessionId = sessionId,
                        timestamp = timestamp,
                    )
                    if (record != null) {
                        records.add(record)
                    }
                }
            }
        }

        return records
    }

    private fun buildIngestRecord(
        metricGroup: MetricGroup,
        agentId: String,
        agentName: String,
        sessionId: String,
        timestamp: String,
    ): IngestRecord? {
        if (metricGroup.inputTokens == 0L && metricGroup.outputTokens == 0L) return null

        return IngestRecord(
            sessionId = sessionId,
            agentId = agentId,
            agentName = agentName,
            modelId = metricGroup.modelId ?: "unknown",
            calledAt = timestamp,
            inputTokens = metricGroup.inputTokens,
            outputTokens = metricGroup.outputTokens,
            cacheReadTokens = metricGroup.cacheReadTokens,
            cacheWriteTokens = metricGroup.cacheWriteTokens,
            latencyMs = metricGroup.latencyMs,
            toolName = metricGroup.toolName,
            error = metricGroup.error,
        )
    }

    private fun groupMetricsByTimestamp(metrics: List<Metric>): Map<String, MetricGroup> {
        val groups = mutableMapOf<String, MetricGroup>()

        for (metric in metrics) {
            val dataPoints = extractDataPoints(metric)
            for (point in dataPoints) {
                val timestamp = formatTimestamp(point.timeUnixNano)
                val group = groups.getOrPut(timestamp) { MetricGroup() }
                val attrs = point.attributesList.toAttributeMap()

                if (group.modelId == null) {
                    group.modelId = attrs["model.id"] ?: attrs["gen_ai.response.model"] ?: attrs["llm.model"]
                }
                if (group.toolName == null) {
                    group.toolName = attrs["tool.name"]
                }
                if (group.error == null) {
                    group.error = attrs["error.message"]
                }

                val value = extractLongValue(point)
                when (metric.name) {
                    "llm.token.input", "gen_ai.client.token.usage.input", "llm.input_tokens" ->
                        group.inputTokens += value
                    "llm.token.output", "gen_ai.client.token.usage.output", "llm.output_tokens" ->
                        group.outputTokens += value
                    "llm.token.cache_read", "llm.cache_read_tokens" ->
                        group.cacheReadTokens += value
                    "llm.token.cache_write", "llm.cache_write_tokens" ->
                        group.cacheWriteTokens += value
                    "llm.latency", "gen_ai.client.operation.duration", "llm.latency_ms" ->
                        group.latencyMs = value
                }
            }
        }

        return groups
    }

    private fun extractDataPoints(metric: Metric): List<NumberDataPoint> =
        when {
            metric.hasSum() -> metric.sum.dataPointsList
            metric.hasGauge() -> metric.gauge.dataPointsList
            else -> emptyList()
        }

    private fun extractLongValue(point: NumberDataPoint): Long =
        when {
            point.hasAsInt() -> point.asInt
            point.hasAsDouble() -> point.asDouble.toLong()
            else -> 0L
        }

    private fun formatTimestamp(timeUnixNano: Long): String {
        if (timeUnixNano == 0L) {
            return LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        }
        val instant = Instant.ofEpochSecond(
            TimeUnit.NANOSECONDS.toSeconds(timeUnixNano),
            timeUnixNano % 1_000_000_000,
        )
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).format(TIMESTAMP_FORMATTER)
    }

    private fun List<KeyValue>.toAttributeMap(): Map<String, String> =
        associate { it.key to it.value.stringValue }
}

private class MetricGroup {
    var modelId: String? = null
    var toolName: String? = null
    var error: String? = null
    var inputTokens: Long = 0L
    var outputTokens: Long = 0L
    var cacheReadTokens: Long = 0L
    var cacheWriteTokens: Long = 0L
    var latencyMs: Long = 0L
}
