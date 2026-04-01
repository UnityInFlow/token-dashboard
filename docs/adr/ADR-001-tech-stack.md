# ADR-001: Technology Stack

## Status

Accepted

## Context

token-dashboard is a real-time AI agent cost monitoring dashboard. It needs to:
- Ingest metrics from budget-breaker via OpenTelemetry
- Store session and cost data locally
- Serve a web dashboard with live updates
- Provide a REST API for integration

## Decision

- **Server**: Ktor 3.x (Kotlin-first, coroutine-native, lightweight)
- **Frontend**: HTMX + Pico CSS (zero JS build step, server-rendered)
- **Database**: SQLite via Exposed ORM (zero setup, local-first)
- **Metrics ingestion**: OTLP/gRPC receiver + REST fallback
- **Build**: Gradle Kotlin DSL
- **Distribution**: Docker image + standalone fat JAR

## Alternatives Considered

| Alternative | Why rejected |
|---|---|
| Spring WebFlux | Heavier dependency graph, slower startup for a simple dashboard |
| PostgreSQL | Adds operational burden; SQLite sufficient for single-node local tool |
| React/Vue | Adds JS build toolchain; HTMX achieves live updates without it |
| InfluxDB | Over-engineered for v0.1 data volume |

## Consequences

- Fast development cycle with Ktor's lightweight setup
- Zero-config local experience (just run the JAR or Docker image)
- SQLite limits horizontal scaling — acceptable for v0.1, PostgreSQL adapter can be added later
- HTMX requires server-side rendering for all UI — fits Ktor HTML DSL well
