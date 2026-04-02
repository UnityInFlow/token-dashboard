package dev.unityinflow.tokendashboard.otlp

import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.ingestion.IngestionService
import io.grpc.ManagedChannelBuilder
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.metrics.v1.Metric
import io.opentelemetry.proto.metrics.v1.NumberDataPoint
import io.opentelemetry.proto.metrics.v1.ResourceMetrics
import io.opentelemetry.proto.metrics.v1.ScopeMetrics
import io.opentelemetry.proto.metrics.v1.Sum
import io.opentelemetry.proto.resource.v1.Resource
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit

class OtlpMetricsReceiverTest {
    private val testPort = 14317
    private lateinit var server: OtlpGrpcServer
    private lateinit var db: org.jetbrains.exposed.sql.Database

    @BeforeEach
    fun setUp() {
        val dbFile = File.createTempFile("td-otlp-", ".db")
        dbFile.deleteOnExit()
        db = DatabaseFactory.init(dbFile.absolutePath)

        val ingestionService = IngestionService(db)
        val receiver = OtlpMetricsReceiver(ingestionService)
        server = OtlpGrpcServer(testPort, receiver)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `OTLP exporter can push token metrics and they appear in the database`() {
        val channel = ManagedChannelBuilder.forAddress("localhost", testPort)
            .usePlaintext()
            .build()

        try {
            val stub = MetricsServiceGrpc.newBlockingStub(channel)

            val timestampNanos = System.currentTimeMillis() * 1_000_000L

            val request = ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(
                    ResourceMetrics.newBuilder()
                        .setResource(
                            Resource.newBuilder()
                                .addAttributes(stringAttr("agent.id", "test-agent-01"))
                                .addAttributes(stringAttr("agent.name", "TestOtlpAgent"))
                                .addAttributes(stringAttr("session.id", "otlp-sess-1")),
                        )
                        .addScopeMetrics(
                            ScopeMetrics.newBuilder()
                                .addMetrics(
                                    sumMetric(
                                        name = "llm.token.input",
                                        value = 1500,
                                        timestampNanos = timestampNanos,
                                        attrs = listOf(stringAttr("model.id", "claude-sonnet-4-20250514")),
                                    ),
                                )
                                .addMetrics(
                                    sumMetric(
                                        name = "llm.token.output",
                                        value = 750,
                                        timestampNanos = timestampNanos,
                                        attrs = listOf(stringAttr("model.id", "claude-sonnet-4-20250514")),
                                    ),
                                )
                                .addMetrics(
                                    sumMetric(
                                        name = "llm.latency",
                                        value = 1200,
                                        timestampNanos = timestampNanos,
                                        attrs = emptyList(),
                                    ),
                                ),
                        ),
                )
                .build()

            stub.export(request)

            val sessions = transaction(db) { SessionsTable.selectAll().toList() }
            sessions.size shouldBe 1
            sessions[0][SessionsTable.agentId] shouldBe "test-agent-01"
            sessions[0][SessionsTable.agentName] shouldBe "TestOtlpAgent"
            sessions[0][SessionsTable.totalInputTokens] shouldBe 1500
            sessions[0][SessionsTable.totalOutputTokens] shouldBe 750

            val calls = transaction(db) { AgentCallsTable.selectAll().toList() }
            calls.size shouldBe 1
            calls[0][AgentCallsTable.modelId] shouldBe "claude-sonnet-4-20250514"
            calls[0][AgentCallsTable.inputTokens] shouldBe 1500
            calls[0][AgentCallsTable.outputTokens] shouldBe 750
            calls[0][AgentCallsTable.latencyMs] shouldBe 1200
            calls[0][AgentCallsTable.costMicros] shouldBeGreaterThan 0L
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `OTLP export with no token metrics returns success without creating records`() {
        val channel = ManagedChannelBuilder.forAddress("localhost", testPort)
            .usePlaintext()
            .build()

        try {
            val stub = MetricsServiceGrpc.newBlockingStub(channel)

            val request = ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(
                    ResourceMetrics.newBuilder()
                        .setResource(
                            Resource.newBuilder()
                                .addAttributes(stringAttr("service.name", "some-service")),
                        )
                        .addScopeMetrics(
                            ScopeMetrics.newBuilder()
                                .addMetrics(
                                    sumMetric(
                                        name = "http.request.count",
                                        value = 42,
                                        timestampNanos = System.currentTimeMillis() * 1_000_000L,
                                        attrs = emptyList(),
                                    ),
                                ),
                        ),
                )
                .build()

            stub.export(request)

            val sessionCount = transaction(db) { SessionsTable.selectAll().count() }
            sessionCount shouldBe 0
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `OTLP export supports gen_ai semantic convention metric names`() {
        val channel = ManagedChannelBuilder.forAddress("localhost", testPort)
            .usePlaintext()
            .build()

        try {
            val stub = MetricsServiceGrpc.newBlockingStub(channel)
            val timestampNanos = System.currentTimeMillis() * 1_000_000L

            val request = ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(
                    ResourceMetrics.newBuilder()
                        .setResource(
                            Resource.newBuilder()
                                .addAttributes(stringAttr("service.name", "budget-breaker"))
                                .addAttributes(stringAttr("session.id", "gen-ai-sess")),
                        )
                        .addScopeMetrics(
                            ScopeMetrics.newBuilder()
                                .addMetrics(
                                    sumMetric(
                                        name = "gen_ai.client.token.usage.input",
                                        value = 2000,
                                        timestampNanos = timestampNanos,
                                        attrs = listOf(stringAttr("gen_ai.response.model", "claude-opus-4-20250514")),
                                    ),
                                )
                                .addMetrics(
                                    sumMetric(
                                        name = "gen_ai.client.token.usage.output",
                                        value = 1000,
                                        timestampNanos = timestampNanos,
                                        attrs = listOf(stringAttr("gen_ai.response.model", "claude-opus-4-20250514")),
                                    ),
                                ),
                        ),
                )
                .build()

            stub.export(request)

            val calls = transaction(db) { AgentCallsTable.selectAll().toList() }
            calls.size shouldBe 1
            calls[0][AgentCallsTable.modelId] shouldBe "claude-opus-4-20250514"
            calls[0][AgentCallsTable.inputTokens] shouldBe 2000
            calls[0][AgentCallsTable.outputTokens] shouldBe 1000
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun stringAttr(key: String, value: String): KeyValue =
        KeyValue.newBuilder()
            .setKey(key)
            .setValue(AnyValue.newBuilder().setStringValue(value))
            .build()

    private fun sumMetric(
        name: String,
        value: Long,
        timestampNanos: Long,
        attrs: List<KeyValue>,
    ): Metric =
        Metric.newBuilder()
            .setName(name)
            .setSum(
                Sum.newBuilder()
                    .addDataPoints(
                        NumberDataPoint.newBuilder()
                            .setAsInt(value)
                            .setTimeUnixNano(timestampNanos)
                            .addAllAttributes(attrs),
                    ),
            )
            .build()
}
