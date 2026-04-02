package dev.unityinflow.tokendashboard.web

import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe

@Suppress("ktlint:standard:max-line-length")
private fun dashboardCss(): String {
    val sb = StringBuilder()
    sb.appendLine("<style>")
    sb.appendLine(":root { --pico-font-size: 15px; }")
    sb.appendLine(
        ".grid-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }",
    )
    sb.appendLine(
        ".stat-card { background: var(--pico-card-background-color); border: 1px solid var(--pico-muted-border-color); border-radius: var(--pico-border-radius); padding: 1.25rem; }",
    )
    sb.appendLine(
        ".stat-card h3 { margin: 0 0 0.25rem; font-size: 0.85rem; color: var(--pico-muted-color); text-transform: uppercase; letter-spacing: 0.05em; }",
    )
    sb.appendLine(".stat-card .value { font-size: 1.75rem; font-weight: 700; margin: 0; }")
    sb.appendLine(".stat-card .sub { font-size: 0.8rem; color: var(--pico-muted-color); margin: 0.25rem 0 0; }")
    sb.appendLine("table { font-size: 0.9rem; }")
    sb.appendLine("nav ul { margin: 0; }")
    sb.appendLine(".htmx-indicator { opacity: 0; transition: opacity 200ms ease-in; }")
    sb.appendLine(".htmx-request .htmx-indicator, .htmx-request.htmx-indicator { opacity: 1; }")
    sb.appendLine(".badge { display: inline-block; padding: 0.15rem 0.5rem; border-radius: 999px; font-size: 0.75rem; font-weight: 600; }")
    sb.appendLine(".badge-active { background: #22c55e22; color: #16a34a; }")
    sb.appendLine(".badge-ended { background: #6b728022; color: #6b7280; }")
    sb.appendLine(".badge-enabled { background: #3b82f622; color: #2563eb; }")
    sb.appendLine(".badge-disabled { background: #ef444422; color: #dc2626; }")
    sb.appendLine(".chart-container { position: relative; height: 300px; margin-bottom: 1.5rem; }")
    sb.appendLine("</style>")
    return sb.toString()
}

fun HTML.layout(
    pageTitle: String,
    activePage: String = "",
    content: kotlinx.html.MAIN.() -> Unit,
) {
    head {
        meta { charset = "utf-8" }
        meta {
            name = "viewport"
            this.content = "width=device-width, initial-scale=1"
        }
        title { +"$pageTitle — Token Dashboard" }
        link {
            rel = "stylesheet"
            href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css"
        }
        script { src = "https://unpkg.com/htmx.org@2.0.4" }
        script { src = "https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js" }
        unsafe {
            +dashboardCss()
        }
    }
    body {
        attributes["data-theme"] = "light"
        nav {
            attributes["class"] = "container-fluid"
            ul {
                li {
                    h1 {
                        attributes["style"] = "margin: 0; font-size: 1.2rem;"
                        +"Token Dashboard"
                    }
                }
            }
            ul {
                li { a(href = "/", classes = if (activePage == "dashboard") "contrast" else "") { +"Dashboard" } }
                li { a(href = "/sessions", classes = if (activePage == "sessions") "contrast" else "") { +"Sessions" } }
                li { a(href = "/agents", classes = if (activePage == "agents") "contrast" else "") { +"Agents" } }
                li { a(href = "/models", classes = if (activePage == "models") "contrast" else "") { +"Models" } }
                li { a(href = "/alerts", classes = if (activePage == "alerts") "contrast" else "") { +"Alerts" } }
            }
        }
        main(classes = "container") {
            content()
        }
        footer(classes = "container") {
            p {
                attributes["style"] = "text-align: center; font-size: 0.8rem; color: var(--pico-muted-color);"
                +"Token Dashboard v0.1.0 · "
                a(href = "https://github.com/UnityInFlow/token-dashboard") { +"GitHub" }
            }
        }
    }
}

fun formatMicros(micros: Long): String {
    val dollars = micros / 1_000_000.0
    return when {
        dollars >= 1.0 -> "$${String.format("%.2f", dollars)}"
        dollars >= 0.01 -> "$${String.format("%.4f", dollars)}"
        else -> "$${String.format("%.6f", dollars)}"
    }
}

fun formatTokens(tokens: Long): String =
    when {
        tokens >= 1_000_000 -> "${String.format("%.1f", tokens / 1_000_000.0)}M"
        tokens >= 1_000 -> "${String.format("%.1f", tokens / 1_000.0)}K"
        else -> tokens.toString()
    }
