package dev.unityinflow.tokendashboard.otlp

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.metrics.v1.Metric
import io.opentelemetry.proto.metrics.v1.NumberDataPoint
import io.opentelemetry.proto.metrics.v1.ResourceMetrics
import io.opentelemetry.proto.metrics.v1.ScopeMetrics
import io.opentelemetry.proto.metrics.v1.Sum
import io.opentelemetry.proto.resource.v1.Resource
import org.junit.jupiter.api.Test

class OtlpMetricMapperTest {

    @Test
    fun `maps resource attributes to agent identity fields`() {
        val request = buildRequest(
            resourceAttrs = mapOf(
                "agent.id" to "my-agent",
                "agent.name" to "MyAgent",
                "session.id" to "sess-42",
            ),
            metrics = listOf("llm.token.input" to 100L, "llm.token.output" to 50L),
        )

        val records = OtlpMetricMapper.mapToIngestRecords(request)
        records shouldHaveSize 1
        records[0].agentId shouldBe "my-agent"
        records[0].agentName shouldBe "MyAgent"
        records[0].sessionId shouldBe "sess-42"
    }

    @Test
    fun `falls back to service name and instance id when agent attrs missing`() {
        val request = buildRequest(
            resourceAttrs = mapOf(
                "service.name" to "budget-breaker",
                "service.instance.id" to "instance-7",
            ),
            metrics = listOf("llm.token.input" to 200L, "llm.token.output" to 100L),
        )

        val records = OtlpMetricMapper.mapToIngestRecords(request)
        records shouldHaveSize 1
        records[0].agentId shouldBe "instance-7"
        records[0].agentName shouldBe "budget-breaker"
    }

    @Test
    fun `skips records with zero input and output tokens`() {
        val request = buildRequest(
            resourceAttrs = mapOf("agent.id" to "a1", "agent.name" to "A"),
            metrics = listOf("llm.latency" to 500L),
        )

        val records = OtlpMetricMapper.mapToIngestRecords(request)
        records.shouldBeEmpty()
    }

    @Test
    fun `extracts model id from data point attributes`() {
        val request = buildRequest(
            resourceAttrs = mapOf("agent.id" to "a1", "agent.name" to "A", "session.id" to "s1"),
            metrics = listOf("llm.token.input" to 300L, "llm.token.output" to 150L),
            metricAttrs = mapOf("model.id" to "claude-haiku-4-5-20251001"),
        )

        val records = OtlpMetricMapper.mapToIngestRecords(request)
        records shouldHaveSize 1
        records[0].modelId shouldBe "claude-haiku-4-5-20251001"
    }

    @Test
    fun `maps cache token metrics`() {
        val request = buildRequest(
            resourceAttrs = mapOf("agent.id" to "a1", "agent.name" to "A", "session.id" to "s1"),
            metrics = listOf(
                "llm.token.input" to 500L,
                "llm.token.output" to 250L,
                "llm.token.cache_read" to 1000L,
                "llm.token.cache_write" to 200L,
            ),
        )

        val records = OtlpMetricMapper.mapToIngestRecords(request)
        records shouldHaveSize 1
        records[0].cacheReadTokens shouldBe 1000
        records[0].cacheWriteTokens shouldBe 200
    }

    @Test
    fun `empty request returns no records`() {
        val request = ExportMetricsServiceRequest.getDefaultInstance()
        val records = OtlpMetricMapper.mapToIngestRecords(request)
        records.shouldBeEmpty()
    }

    private fun buildRequest(
        resourceAttrs: Map<String, String>,
        metrics: List<Pair<String, Long>>,
        metricAttrs: Map<String, String> = emptyMap(),
    ): ExportMetricsServiceRequest {
        val timestampNanos = System.currentTimeMillis() * 1_000_000L
        val pointAttrs = metricAttrs.map { (k, v) ->
            KeyValue.newBuilder()
                .setKey(k)
                .setValue(AnyValue.newBuilder().setStringValue(v))
                .build()
        }

        val scopeMetricsBuilder = ScopeMetrics.newBuilder()
        for ((name, value) in metrics) {
            scopeMetricsBuilder.addMetrics(
                Metric.newBuilder()
                    .setName(name)
                    .setSum(
                        Sum.newBuilder()
                            .addDataPoints(
                                NumberDataPoint.newBuilder()
                                    .setAsInt(value)
                                    .setTimeUnixNano(timestampNanos)
                                    .addAllAttributes(pointAttrs),
                            ),
                    )
                    .build(),
            )
        }

        val resourceBuilder = Resource.newBuilder()
        for ((k, v) in resourceAttrs) {
            resourceBuilder.addAttributes(
                KeyValue.newBuilder()
                    .setKey(k)
                    .setValue(AnyValue.newBuilder().setStringValue(v))
                    .build(),
            )
        }

        return ExportMetricsServiceRequest.newBuilder()
            .addResourceMetrics(
                ResourceMetrics.newBuilder()
                    .setResource(resourceBuilder)
                    .addScopeMetrics(scopeMetricsBuilder),
            )
            .build()
    }
}
