package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.domain.BudgetAlert
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

fun FlowContent.alertsContent(alerts: List<BudgetAlert>) {
    h2 { +"Budget Alerts" }

    div(classes = "grid") {
        div {
            h3 { +"Active Alerts" }
            div {
                attributes["id"] = "alerts-table"
                attributes["hx-get"] = "/htmx/alerts-table"
                attributes["hx-trigger"] = "every 5s"
                attributes["hx-swap"] = "innerHTML"
                alertsTableContent(alerts)
            }
        }

        div {
            h3 { +"Create Alert" }
            alertForm()
        }
    }
}

fun FlowContent.alertsTableContent(alerts: List<BudgetAlert>) {
    if (alerts.isEmpty()) {
        p { +"No alerts configured. Create one using the form." }
        return
    }
    table {
        attributes["role"] = "grid"
        thead {
            tr {
                th { +"Name" }
                th { +"Agent" }
                th { +"Threshold" }
                th { +"Period" }
                th { +"Status" }
                th { +"Actions" }
            }
        }
        tbody {
            alerts.forEach { alert ->
                tr {
                    td { +alert.name }
                    td { +(alert.agentId ?: "All") }
                    td { +formatMicros(alert.thresholdMicros) }
                    td { +alert.period.name.lowercase().replaceFirstChar { it.uppercase() } }
                    td {
                        val statusClass = if (alert.enabled) "badge-enabled" else "badge-disabled"
                        val statusText = if (alert.enabled) "Active" else "Disabled"
                        span(classes = "badge $statusClass") { +statusText }
                    }
                    td {
                        button {
                            attributes["hx-delete"] = "/api/v1/alerts/${alert.id}"
                            attributes["hx-target"] = "#alerts-table"
                            attributes["hx-swap"] = "innerHTML"
                            attributes["hx-confirm"] = "Delete alert '${alert.name}'?"
                            attributes["class"] = "outline secondary"
                            attributes["style"] = "padding: 0.25rem 0.5rem; font-size: 0.8rem;"
                            type = ButtonType.button
                            +"Delete"
                        }
                    }
                }
            }
        }
    }
}

fun FlowContent.alertForm() {
    div {
        attributes["hx-post"] = "/api/v1/alerts"
        attributes["hx-target"] = "#alerts-table"
        attributes["hx-swap"] = "innerHTML"
        attributes["hx-ext"] = "json-enc"

        label {
            +"Name"
            input {
                type = InputType.text
                name = "name"
                placeholder = "Daily budget alert"
                required = true
            }
        }
        label {
            +"Agent ID (optional, leave blank for all)"
            input {
                type = InputType.text
                name = "agentId"
                placeholder = "agent-id"
            }
        }
        label {
            +"Threshold (USD)"
            input {
                type = InputType.number
                name = "thresholdUsd"
                placeholder = "5.00"
                step = "0.01"
                required = true
            }
        }
        label {
            +"Period"
            select {
                name = "period"
                option {
                    value = "DAILY"
                    +"Daily"
                }
                option {
                    value = "WEEKLY"
                    +"Weekly"
                }
                option {
                    value = "MONTHLY"
                    +"Monthly"
                }
                option {
                    value = "SESSION"
                    +"Per Session"
                }
            }
        }
        label {
            +"Webhook URL (optional)"
            input {
                type = InputType.url
                name = "webhookUrl"
                placeholder = "https://hooks.slack.com/..."
            }
        }
        button {
            type = ButtonType.submit
            +"Create Alert"
        }
    }
}
