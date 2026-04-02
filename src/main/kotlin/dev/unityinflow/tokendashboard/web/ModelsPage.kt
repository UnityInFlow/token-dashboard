package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.domain.ModelCostBreakdown
import kotlinx.html.FlowContent
import kotlinx.html.canvas
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.unsafe

fun FlowContent.modelsContent(models: List<ModelCostBreakdown>) {
    h2 { +"Model Comparison" }

    if (models.isEmpty()) {
        p { +"No model data yet. Ingest some calls to see model comparison." }
        return
    }

    div(classes = "grid-stats") {
        models.forEach { model ->
            statCard(
                label = model.modelId,
                value = formatMicros(model.totalCostMicros),
                sub = "${formatTokens(
                    model.totalInputTokens,
                )} in · ${formatTokens(model.totalOutputTokens)} out · ${model.callCount} calls",
            )
        }
    }

    div(classes = "chart-container") {
        canvas { attributes["id"] = "modelCostChart" }
    }

    modelCostChartScript(models)

    h2 { +"Detailed Breakdown" }
    table {
        attributes["role"] = "grid"
        thead {
            tr {
                th { +"Model" }
                th { +"Calls" }
                th { +"Input Tokens" }
                th { +"Output Tokens" }
                th { +"Total Cost" }
                th { +"Avg Cost/Call" }
            }
        }
        tbody {
            models.forEach { model ->
                val avgCost = if (model.callCount > 0) model.totalCostMicros / model.callCount else 0L
                tr {
                    td { +model.modelId }
                    td { +model.callCount.toString() }
                    td { +formatTokens(model.totalInputTokens) }
                    td { +formatTokens(model.totalOutputTokens) }
                    td { +formatMicros(model.totalCostMicros) }
                    td { +formatMicros(avgCost) }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:max-line-length")
private fun FlowContent.modelCostChartScript(models: List<ModelCostBreakdown>) {
    val labelsJson = models.joinToString(",") { "\"${it.modelId}\"" }
    val costValues = models.joinToString(",") { String.format("%.4f", it.totalCostMicros / 1_000_000.0) }

    val colors =
        listOf(
            "rgba(59, 130, 246, 0.7)",
            "rgba(16, 185, 129, 0.7)",
            "rgba(245, 158, 11, 0.7)",
            "rgba(239, 68, 68, 0.7)",
            "rgba(139, 92, 246, 0.7)",
            "rgba(236, 72, 153, 0.7)",
        )
    val borders =
        listOf(
            "rgb(59, 130, 246)",
            "rgb(16, 185, 129)",
            "rgb(245, 158, 11)",
            "rgb(239, 68, 68)",
            "rgb(139, 92, 246)",
            "rgb(236, 72, 153)",
        )
    val bgJson = models.indices.joinToString(",") { "\"${colors[it % colors.size]}\"" }
    val borderJson = models.indices.joinToString(",") { "\"${borders[it % borders.size]}\"" }

    val js = buildModelChartJs(labelsJson, costValues, bgJson, borderJson)
    script {
        unsafe {
            +js
        }
    }
}

@Suppress("ktlint:standard:max-line-length")
private fun buildModelChartJs(
    labelsJson: String,
    costValues: String,
    bgJson: String,
    borderJson: String,
): String {
    val sb = StringBuilder()
    sb.appendLine("(function(){")
    sb.appendLine("var ctx=document.getElementById('modelCostChart');")
    sb.appendLine("if(!ctx)return;")
    sb.appendLine("if(window._modelCostChart)window._modelCostChart.destroy();")
    sb.appendLine("window._modelCostChart=new Chart(ctx,{type:'bar',")
    sb.appendLine("data:{labels:[$labelsJson],datasets:[{label:'Total Cost (USD)',data:[$costValues],")
    sb.appendLine("backgroundColor:[$bgJson],borderColor:[$borderJson],borderWidth:1,borderRadius:4}]},")
    sb.appendLine("options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},")
    sb.appendLine("scales:{y:{beginAtZero:true,ticks:{callback:function(v){return '\$'+v.toFixed(2)}}}}}});")
    sb.appendLine("})();")
    return sb.toString()
}
