package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.domain.AgentCostBreakdown
import dev.unityinflow.tokendashboard.domain.Session
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

fun FlowContent.agentsContent(agents: List<AgentCostBreakdown>) {
    h2 { +"Agents" }
    div {
        attributes["id"] = "agents-grid"
        attributes["hx-get"] = "/htmx/agents-grid"
        attributes["hx-trigger"] = "every 10s"
        attributes["hx-swap"] = "innerHTML"
        agentsGridContent(agents)
    }
}

fun FlowContent.agentsGridContent(agents: List<AgentCostBreakdown>) {
    if (agents.isEmpty()) {
        p { +"No agents found. Ingest some calls to see agent data." }
        return
    }
    div(classes = "grid-stats") {
        agents.forEach { agent ->
            div(classes = "stat-card") {
                h3 { a(href = "/agents/${agent.agentId}") { +agent.agentName } }
                p(classes = "value") { +formatMicros(agent.totalCostMicros) }
                p(classes = "sub") { +"${agent.sessionCount} sessions · avg ${formatMicros(agent.avgSessionCostMicros)}/session" }
            }
        }
    }
}

fun FlowContent.agentDetailContent(
    agent: AgentCostBreakdown,
    sessions: List<Session>,
) {
    h2 { +"Agent: ${agent.agentName}" }
    div(classes = "grid-stats") {
        statCard("Total Cost", formatMicros(agent.totalCostMicros))
        statCard("Sessions", agent.sessionCount.toString())
        statCard("Avg Cost/Session", formatMicros(agent.avgSessionCostMicros))
    }

    h3 { +"Sessions" }
    if (sessions.isEmpty()) {
        p { +"No sessions for this agent." }
    } else {
        table {
            attributes["role"] = "grid"
            thead {
                tr {
                    th { +"Started" }
                    th { +"Input Tokens" }
                    th { +"Output Tokens" }
                    th { +"Cost" }
                    th { +"Details" }
                }
            }
            tbody {
                sessions.forEach { session ->
                    tr {
                        td { +session.startedAt.take(16).replace("T", " ") }
                        td { +formatTokens(session.totalInputTokens) }
                        td { +formatTokens(session.totalOutputTokens) }
                        td { +formatMicros(session.totalCostMicros) }
                        td { a(href = "/sessions/${session.id}") { +"View" } }
                    }
                }
            }
        }
    }

    p { a(href = "/agents") { +"← Back to agents" } }
}
