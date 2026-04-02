# token-dashboard — Real-Time Token Burn Dashboard

## Project Overview

**Tool 06** in the [UnityInFlow](https://github.com/UnityInFlow) ecosystem.

Real-time dashboard for AI agent token consumption: burn rate per agent, cost forecast, anomaly detection, and budget alerts. Consumes Micrometer metrics from budget-breaker and KORE via OTLP. HTMX frontend with zero JavaScript build step, SQLite local-first storage.

**Phase:** 2 | **Stack:** Kotlin (Ktor) + HTMX | **Distribution:** Docker image + standalone JAR

## Status

v0.0.1 complete — 99 tests, all features implemented. Pending release tag.

## Reference Documents

- `06-token-dashboard.md` — Feature spec, key features checklist, technical stack, implementation todos (Weeks 10-12)
- `claude-code-harness-engineering-guide-v2.md` — Harness engineering patterns and best practices

Read these before making architectural or scope decisions.

## Tooling

| Tool | Status | Usage |
|---|---|---|
| **GSD** | Installed (global) | `/gsd:new-project` to scaffold when ready. `/gsd:plan-phase` and `/gsd:execute-phase` for structured development. |
| **RTK** | Active (v0.34.2) | Automatic via hooks. Compresses gradle, git, docker output. ~80% token savings. |
| **Superpowers** | Active (v5.0.5) | Auto-triggers brainstorming, TDD, planning, code review, debugging skills. |

## Constraints

### Kotlin (inherited from ecosystem CLAUDE.md)
- Kotlin 2.0+, JVM target 21
- Gradle (Kotlin DSL only — never Groovy)
- Test with JUnit 5 + Kotest matchers
- Coroutines for all async work — never `Thread.sleep()` or raw threads
- Immutable data classes preferred over mutable state
- Sealed classes for domain modelling (results, errors, states)
- No `var` — always `val`, refactor if mutation seems needed
- No `!!` without a comment explaining why it's safe
- `ktlint` before every commit
- Group: `dev.unityinflow`

### Ktor-specific
- Ktor is lighter than Spring WebFlux for simple dashboard serving
- HTMX means zero JavaScript build step
- SQLite via Exposed ORM — zero database server required for local use

### General
- Test coverage >80% on core logic before release
- No secrets committed — all credentials via environment variables

## Acceptance Criteria — v0.0.1

- [ ] Real-time burn rate display: tokens/minute, projected session cost
- [ ] Per-agent cost breakdown with sortable table
- [ ] Per-model breakdown: compare Opus vs Sonnet vs Haiku costs
- [ ] Session history: cost trends over last 30 days
- [ ] Anomaly detection: flag agents running 2x+ normal cost
- [ ] Budget alert webhooks: Slack and custom URL on threshold breach
- [ ] SQLite backend: local-first, zero cloud dependency
- [ ] HTMX frontend: live updates without JavaScript framework
- [ ] OTLP receiver: ingests metrics from budget-breaker and KORE
- [ ] Docker one-liner: `docker run -p 8080:8080 unityinflow/token-dashboard`
- [ ] REST API for integration with other tools

## Development Workflow

When ready to build:

1. `/gsd:new-project` — describe token-dashboard, feed existing spec
2. `/gsd:discuss-phase 1` — lock in decisions for Week 10 (Ktor server, SQLite schema, OTLP receiver, REST API)
3. `/gsd:plan-phase 1` — atomic task plans with file paths
4. `/gsd:execute-phase 1` — parallel execution with fresh context windows
5. `/gsd:discuss-phase 2` — lock in decisions for Weeks 11-12 (HTMX dashboard, Slack webhooks, Docker image)
6. `/gsd:plan-phase 2` — atomic task plans
7. `/gsd:execute-phase 2` — build and ship

Superpowers skills (TDD, code review, debugging) activate automatically during execution.

## Key Dependencies (for reference, not installed yet)

- `ktor-server-core` — HTTP server
- `exposed` — SQLite ORM
- `opentelemetry-sdk` — OTLP receiver
- `ktor-server-html-builder` — server-side HTML rendering

---

## CI / Self-Hosted Runners

Use UnityInFlow org-level self-hosted runners. Never use `ubuntu-latest`.

```yaml
runs-on: [arc-runner-unityinflow]
```

Available runners: `hetzner-runner-1/2/3` (X64), `orangepi-runner` (ARM64).

---

## Do Not

- Do not use a JavaScript framework for the frontend — HTMX only
- Do not require a separate database server — SQLite is the default
- Do not use `var` in Kotlin — always `val`
- Do not use `!!` without a comment explaining why it's safe
- Do not commit secrets or API keys
- Do not skip writing tests
- Do not inline the reference docs into this file — read them by path
