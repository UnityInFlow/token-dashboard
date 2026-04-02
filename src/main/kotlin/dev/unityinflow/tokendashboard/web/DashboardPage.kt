package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.domain.AgentCostBreakdown
import dev.unityinflow.tokendashboard.domain.BudgetAlert
import dev.unityinflow.tokendashboard.domain.BurnRate
import dev.unityinflow.tokendashboard.domain.CostSummary
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.canvas
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.unsafe

fun FlowContent.dashboardContent(
    summary: CostSummary,
    topAgents: List<AgentCostBreakdown>,
    recentAlerts: List<BudgetAlert>,
) {
    h2 { +"Dashboard" }

    div(classes = "grid-stats") {
        statCard("Today", formatMicros(summary.todayMicros))
        statCard("This Week", formatMicros(summary.thisWeekMicros))
        statCard("This Month", formatMicros(summary.thisMonthMicros))
        statCard("Active Sessions", summary.activeSessions.toString(), sub = "${summary.totalSessions} total")
    }

    div {
        attributes["id"] = "burn-rate-container"
        attributes["hx-get"] = "/htmx/burn-rate"
        attributes["hx-trigger"] = "load, every 15s"
        attributes["hx-swap"] = "innerHTML"
    }

    div(classes = "grid") {
        div {
            div(classes = "chart-container") {
                attributes["id"] = "cost-chart-container"
                attributes["hx-get"] = "/htmx/cost-chart-data"
                attributes["hx-trigger"] = "load, every 60s"
                attributes["hx-swap"] = "innerHTML"
                canvas { attributes["id"] = "costChart" }
            }
        }
    }

    div(classes = "grid") {
        div {
            h3 { +"Top Agents" }
            if (topAgents.isEmpty()) {
                p { +"No agent data yet. Ingest some calls to get started." }
            } else {
                table {
                    attributes["role"] = "grid"
                    thead {
                        tr {
                            th { +"Agent" }
                            th { +"Sessions" }
                            th { +"Total Cost" }
                        }
                    }
                    tbody {
                        topAgents.take(5).forEach { agent ->
                            tr {
                                td { a(href = "/agents/${agent.agentId}") { +agent.agentName } }
                                td { +agent.sessionCount.toString() }
                                td { +formatMicros(agent.totalCostMicros) }
                            }
                        }
                    }
                }
            }
        }

        div {
            h3 { +"Budget Alerts" }
            if (recentAlerts.isEmpty()) {
                p {
                    +"No alerts configured. "
                    a(href = "/alerts") { +"Set one up." }
                }
            } else {
                table {
                    attributes["role"] = "grid"
                    thead {
                        tr {
                            th { +"Name" }
                            th { +"Threshold" }
                            th { +"Status" }
                        }
                    }
                    tbody {
                        recentAlerts.take(5).forEach { alert ->
                            tr {
                                td { +alert.name }
                                td { +formatMicros(alert.thresholdMicros) }
                                td {
                                    val statusClass = if (alert.enabled) "badge-enabled" else "badge-disabled"
                                    val statusText = if (alert.enabled) "Active" else "Disabled"
                                    span(classes = "badge $statusClass") { +statusText }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun FlowContent.statCard(
    label: String,
    value: String,
    sub: String? = null,
) {
    div(classes = "stat-card") {
        h3 { +label }
        p(classes = "value") { +value }
        if (sub != null) {
            p(classes = "sub") { +sub }
        }
    }
}

@Suppress("ktlint:standard:max-line-length")
private fun buildChartJs(
    labelsJson: String,
    valuesJson: String,
): String {
    val sb = StringBuilder()
    sb.appendLine("(function(){")
    sb.appendLine("var ctx=document.getElementById('costChart');")
    sb.appendLine("if(!ctx)return;")
    sb.appendLine("if(window._costChart)window._costChart.destroy();")
    sb.appendLine("window._costChart=new Chart(ctx,{type:'bar',")
    sb.appendLine("data:{labels:[$labelsJson],datasets:[{label:'Daily Cost (USD)',data:[$valuesJson],")
    sb.appendLine("backgroundColor:'rgba(59,130,246,0.5)',borderColor:'rgb(59,130,246)',borderWidth:1,borderRadius:4}]},")
    sb.appendLine("options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},")
    sb.appendLine("scales:{y:{beginAtZero:true,ticks:{callback:function(v){return '\$'+v.toFixed(2)}}}}}});")
    sb.appendLine("})();")
    return sb.toString()
}

fun FlowContent.burnRateContent(burnRate: BurnRate) {
    div(classes = "grid-stats") {
        statCard(
            label = "Burn Rate",
            value = "${String.format("%.0f", burnRate.tokensPerMinute)} tok/min",
            sub = "${burnRate.windowMinutes}-min window",
        )
        statCard(
            label = "Projected Hourly Cost",
            value = formatMicros(burnRate.projectedSessionCostMicros),
            sub = "based on current rate",
        )
    }
}

fun FlowContent.costChartScript(
    labels: List<String>,
    values: List<Long>,
) {
    val labelsJson = labels.joinToString(",") { "\"$it\"" }
    val valuesJson = values.joinToString(",") { String.format("%.4f", it / 1_000_000.0) }
    script {
        unsafe {
            +buildChartJs(labelsJson, valuesJson)
        }
    }
}
