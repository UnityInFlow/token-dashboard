package dev.unityinflow.tokendashboard.ingestion

import dev.unityinflow.tokendashboard.db.DatabaseFactory
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

class CostCalculatorTest {
    private fun initTestDb() =
        DatabaseFactory.init(
            File.createTempFile("td-cost-", ".db").also { it.deleteOnExit() }.absolutePath,
        )

    @Test
    fun `calculates cost for known model`() {
        val db = initTestDb()
        val record =
            IngestRecord(
                sessionId = "s1",
                agentId = "a1",
                agentName = "Test",
                modelId = "claude-sonnet-4-20250514",
                calledAt = "2026-04-01T12:00:00",
                inputTokens = 1_000_000,
                outputTokens = 1_000_000,
                latencyMs = 1000,
            )

        // Sonnet: 3M input + 15M output per 1M tokens = 18M microdollars = $18
        val cost = CostCalculator.calculateCostMicros(record, db)
        cost shouldBe 18_000_000
    }

    @Test
    fun `calculates cost with cache tokens`() {
        val db = initTestDb()
        val record =
            IngestRecord(
                sessionId = "s2",
                agentId = "a1",
                agentName = "Test",
                modelId = "claude-sonnet-4-20250514",
                calledAt = "2026-04-01T12:00:00",
                inputTokens = 500_000,
                outputTokens = 200_000,
                cacheReadTokens = 100_000,
                cacheWriteTokens = 50_000,
                latencyMs = 800,
            )

        val cost = CostCalculator.calculateCostMicros(record, db)
        cost shouldBeGreaterThan 0
    }

    @Test
    fun `returns zero for unknown model`() {
        val db = initTestDb()
        val record =
            IngestRecord(
                sessionId = "s3",
                agentId = "a1",
                agentName = "Test",
                modelId = "unknown-model-xyz",
                calledAt = "2026-04-01T12:00:00",
                inputTokens = 1000,
                outputTokens = 500,
                latencyMs = 500,
            )

        val cost = CostCalculator.calculateCostMicros(record, db)
        cost shouldBe 0
    }
}
