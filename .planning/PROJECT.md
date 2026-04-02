# token-dashboard

## What This Is
A real-time web dashboard for AI agent token consumption. Shows burn rate, per-agent/per-model cost breakdowns, session history, anomaly detection, and budget alerts. Kotlin + Ktor + HTMX + SQLite. Tool #06 in the UnityInFlow ecosystem.

## Core Value
See what your AI agents are actually costing — in real time, with anomaly detection and budget alerts.

## Requirements

### Validated
- [x] **DASH-01**: Real-time burn rate display (tokens/minute, projected cost)
- [x] **DASH-02**: Per-agent cost breakdown with sortable table
- [x] **DASH-03**: Per-model breakdown (Opus vs Sonnet vs Haiku costs)
- [x] **DASH-04**: Session history with cost trends
- [x] **DASH-05**: Anomaly detection (flag 2x+ normal cost agents)
- [x] **ALERT-01**: Budget alert webhooks (Slack + custom URL)
- [x] **DB-01**: SQLite backend (local-first, zero cloud dependency)
- [x] **UI-01**: HTMX frontend with live updates
- [x] **OTLP-01**: OTLP receiver for budget-breaker/KORE metrics
- [x] **DOCKER-01**: Docker one-liner support
- [x] **API-01**: REST API for integration

### Out of Scope
- PostgreSQL backend — v0.1.0 (SQLite default, Postgres optional)
- Grafana export — v0.1.0
- Multi-tenant support — v0.1.0

## Context
- Built with Ktor (lightweight, Kotlin-first) not Spring
- HTMX for frontend — zero JavaScript build step
- SQLite via Exposed ORM
- Consumes Micrometer metrics from budget-breaker (#05)
- 99 tests across 16 test files

## Key Decisions
| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Ktor over Spring | Lighter weight for dashboard serving | ✓ Good |
| HTMX over React | No build step, server-rendered HTML | ✓ Good |
| SQLite over Postgres | Zero-config local experience | ✓ Good |
| Exposed ORM | Kotlin-first SQL DSL | ✓ Good |

---
*Last updated: 2026-04-02*
