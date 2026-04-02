package dev.unityinflow.tokendashboard.ingestion

import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.db.tables.ModelCostsTable
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.io.File

class CostCalculatorExtendedTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-costcalc-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    private fun makeRecord(
        modelId: String,
        inputTokens: Long = 1_000_000,
        outputTokens: Long = 1_000_000,
        cacheReadTokens: Long = 0,
        cacheWriteTokens: Long = 0,
    ) = IngestRecord(
        sessionId = "s1",
        agentId = "a1",
        agentName = "Test",
        modelId = modelId,
        calledAt = "2026-04-01T12:00:00",
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cacheReadTokens = cacheReadTokens,
        cacheWriteTokens = cacheWriteTokens,
        latencyMs = 1000,
    )

    @Test
    fun `calculates correct cost for Opus model`() {
        val db = initTestDb()
        // Opus: 15M input + 75M output per 1M tokens = 90M microdollars = $90
        val cost = CostCalculator.calculateCostMicros(
            makeRecord("claude-opus-4-20250514"),
            db,
        )
        cost shouldBe 90_000_000
    }

    @Test
    fun `calculates correct cost for Haiku model`() {
        val db = initTestDb()
        // Haiku: 0.8M input + 4M output per 1M tokens = 4.8M microdollars = $4.80
        val cost = CostCalculator.calculateCostMicros(
            makeRecord("claude-haiku-4-5-20251001"),
            db,
        )
        cost shouldBe 4_800_000
    }

    @Test
    fun `returns zero cost for zero tokens`() {
        val db = initTestDb()
        val cost = CostCalculator.calculateCostMicros(
            makeRecord("claude-sonnet-4-20250514", inputTokens = 0, outputTokens = 0),
            db,
        )
        cost shouldBe 0
    }

    @Test
    fun `uses database pricing when available`() {
        val db = initTestDb()

        // Insert custom pricing: 1M input, 2M output per 1M tokens
        transaction(db) {
            ModelCostsTable.insert {
                it[modelId] = "custom-model"
                it[inputCostPerMtok] = 1_000_000
                it[outputCostPerMtok] = 2_000_000
                it[cacheReadCostPerMtok] = 0
                it[cacheWriteCostPerMtok] = 0
                it[updatedAt] = "2026-04-01T12:00:00"
            }
        }

        val cost = CostCalculator.calculateCostMicros(
            makeRecord("custom-model"),
            db,
        )
        // 1M input + 2M output = 3M microdollars
        cost shouldBe 3_000_000
    }

    @Test
    fun `includes cache costs in total`() {
        val db = initTestDb()
        val record = makeRecord(
            "claude-sonnet-4-20250514",
            inputTokens = 0,
            outputTokens = 0,
            cacheReadTokens = 1_000_000,
            cacheWriteTokens = 1_000_000,
        )

        val cost = CostCalculator.calculateCostMicros(record, db)
        // Cache read: 300K + Cache write: 3.75M = 4.05M microdollars
        cost shouldBe 4_050_000
    }

    @Test
    fun `calculates combined input output and cache costs`() {
        val db = initTestDb()
        val record = makeRecord(
            "claude-sonnet-4-20250514",
            inputTokens = 1_000_000,
            outputTokens = 1_000_000,
            cacheReadTokens = 1_000_000,
            cacheWriteTokens = 1_000_000,
        )

        val cost = CostCalculator.calculateCostMicros(record, db)
        // Input: 3M + Output: 15M + CacheRead: 300K + CacheWrite: 3.75M = 22.05M
        cost shouldBe 22_050_000
    }

    @Test
    fun `handles small token counts with integer division`() {
        val db = initTestDb()
        val record = makeRecord(
            "claude-sonnet-4-20250514",
            inputTokens = 100,
            outputTokens = 50,
        )

        val cost = CostCalculator.calculateCostMicros(record, db)
        // Sonnet: 100 * 3_000_000 / 1_000_000 = 300 (input)
        //         50 * 15_000_000 / 1_000_000 = 750 (output)
        cost shouldBe 1050
    }
}
