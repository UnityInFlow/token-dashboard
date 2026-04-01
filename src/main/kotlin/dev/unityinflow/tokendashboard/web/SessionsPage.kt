package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.domain.AgentCall
import dev.unityinflow.tokendashboard.domain.Session
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

fun FlowContent.sessionsContent(sessions: List<Session>) {
    h2 { +"Sessions" }
    div {
        attributes["id"] = "sessions-table"
        attributes["hx-get"] = "/htmx/sessions-table"
        attributes["hx-trigger"] = "every 10s"
        attributes["hx-swap"] = "innerHTML"
        sessionsTableContent(sessions)
    }
}

fun FlowContent.sessionsTableContent(sessions: List<Session>) {
    if (sessions.isEmpty()) {
        p { +"No sessions yet. Use the ingest endpoint to record LLM calls." }
        return
    }
    table {
        attributes["role"] = "grid"
        thead {
            tr {
                th { +"Agent" }
                th { +"Started" }
                th { +"Status" }
                th { +"Input Tokens" }
                th { +"Output Tokens" }
                th { +"Cost" }
                th { +"Details" }
            }
        }
        tbody {
            sessions.forEach { session ->
                tr {
                    td { +session.agentName }
                    td { +session.startedAt.take(16).replace("T", " ") }
                    td {
                        if (session.endedAt == null) {
                            span(classes = "badge badge-active") { +"Active" }
                        } else {
                            span(classes = "badge badge-ended") { +"Ended" }
                        }
                    }
                    td { +formatTokens(session.totalInputTokens) }
                    td { +formatTokens(session.totalOutputTokens) }
                    td { +formatMicros(session.totalCostMicros) }
                    td { a(href = "/sessions/${session.id}") { +"View" } }
                }
            }
        }
    }
}

fun FlowContent.sessionDetailContent(
    session: Session,
    calls: List<AgentCall>,
) {
    h2 { +"Session: ${session.agentName}" }
    div(classes = "grid-stats") {
        statCard("Agent", session.agentName)
        statCard("Status", if (session.endedAt == null) "Active" else "Ended")
        statCard("Input Tokens", formatTokens(session.totalInputTokens))
        statCard("Output Tokens", formatTokens(session.totalOutputTokens))
        statCard("Total Cost", formatMicros(session.totalCostMicros))
    }

    h3 { +"Calls" }
    if (calls.isEmpty()) {
        p { +"No individual call records for this session." }
    } else {
        table {
            attributes["role"] = "grid"
            thead {
                tr {
                    th { +"Model" }
                    th { +"Input" }
                    th { +"Output" }
                    th { +"Cost" }
                    th { +"Latency" }
                    th { +"Time" }
                }
            }
            tbody {
                calls.forEach { agentCall ->
                    tr {
                        td { +agentCall.modelId }
                        td { +formatTokens(agentCall.inputTokens) }
                        td { +formatTokens(agentCall.outputTokens) }
                        td { +formatMicros(agentCall.costMicros) }
                        td { +"${agentCall.latencyMs}ms" }
                        td { +agentCall.calledAt.take(16).replace("T", " ") }
                    }
                }
            }
        }
    }

    p { a(href = "/sessions") { +"← Back to sessions" } }
}
