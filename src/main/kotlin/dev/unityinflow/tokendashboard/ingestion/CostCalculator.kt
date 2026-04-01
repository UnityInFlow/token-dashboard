package dev.unityinflow.tokendashboard.ingestion

import dev.unityinflow.tokendashboard.db.tables.ModelCostsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

data class ModelPricing(
    val inputCostPerMtok: Long,
    val outputCostPerMtok: Long,
    val cacheReadCostPerMtok: Long,
    val cacheWriteCostPerMtok: Long,
)

object CostCalculator {
    // Default pricing in microdollars per 1M tokens (April 2026 Anthropic pricing)
    private val defaultPricing =
        mapOf(
            "claude-opus-4-20250514" to ModelPricing(15_000_000, 75_000_000, 1_500_000, 18_750_000),
            "claude-sonnet-4-20250514" to ModelPricing(3_000_000, 15_000_000, 300_000, 3_750_000),
            "claude-haiku-4-5-20251001" to ModelPricing(800_000, 4_000_000, 80_000, 1_000_000),
        )

    fun calculateCostMicros(
        record: IngestRecord,
        db: Database,
    ): Long {
        val pricing = lookupPricing(record.modelId, db) ?: return 0L

        val inputCost = record.inputTokens * pricing.inputCostPerMtok / 1_000_000
        val outputCost = record.outputTokens * pricing.outputCostPerMtok / 1_000_000
        val cacheReadCost = record.cacheReadTokens * pricing.cacheReadCostPerMtok / 1_000_000
        val cacheWriteCost = record.cacheWriteTokens * pricing.cacheWriteCostPerMtok / 1_000_000

        return inputCost + outputCost + cacheReadCost + cacheWriteCost
    }

    private fun lookupPricing(
        modelId: String,
        db: Database,
    ): ModelPricing? {
        // Check database first
        val dbPricing =
            transaction(db) {
                ModelCostsTable.selectAll()
                    .where { ModelCostsTable.modelId eq modelId }
                    .singleOrNull()
                    ?.let { row ->
                        ModelPricing(
                            inputCostPerMtok = row[ModelCostsTable.inputCostPerMtok],
                            outputCostPerMtok = row[ModelCostsTable.outputCostPerMtok],
                            cacheReadCostPerMtok = row[ModelCostsTable.cacheReadCostPerMtok],
                            cacheWriteCostPerMtok = row[ModelCostsTable.cacheWriteCostPerMtok],
                        )
                    }
            }
        if (dbPricing != null) return dbPricing

        // Fall back to defaults, try exact match then prefix match
        return defaultPricing[modelId]
            ?: defaultPricing.entries.firstOrNull { modelId.startsWith(it.key.substringBefore("-2")) }?.value
    }
}
