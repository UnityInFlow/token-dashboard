package dev.unityinflow.tokendashboard.db.tables

import org.jetbrains.exposed.sql.Table

object ModelCostsTable : Table("model_costs") {
    val modelId = text("model_id")
    val inputCostPerMtok = long("input_cost_per_mtok")
    val outputCostPerMtok = long("output_cost_per_mtok")
    val cacheReadCostPerMtok = long("cache_read_cost_per_mtok").default(0)
    val cacheWriteCostPerMtok = long("cache_write_cost_per_mtok").default(0)
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(modelId)
}
