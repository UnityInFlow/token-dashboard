# State: token-dashboard

## Project Reference
See: .planning/PROJECT.md (updated 2026-04-02)
**Core value:** Real-time visibility into AI agent costs
**Current focus:** v0.1.0 released (infra). Next: Phase 2 features targeting v0.2.0.

## Phase 1 — COMPLETE

### Deliverables
- [x] Ktor server with REST API (agents, alerts, costs, health, ingest, sessions)
- [x] OTLP gRPC receiver
- [x] SQLite via Exposed ORM (5 tables)
- [x] HTMX web pages (dashboard, agents, models, sessions, alerts)
- [x] Anomaly detection service
- [x] Webhook dispatcher (Slack + custom)
- [x] Cost calculator
- [x] Docker support (Dockerfile + docker-compose)
- [x] 99 tests across 16 test files
- [x] CONTRIBUTING.md, README.md, LICENSE
- [x] v0.0.1 released

### Release
- **Version:** v0.1.0 (current)
- **Repo:** https://github.com/UnityInFlow/token-dashboard
- **v0.0.1** — full feature release (99 tests, all Phase 1 features)
- **v0.1.0 (2026-05-11)** — infra-only release: io.github.unityinflow group rename, fat jar + GHCR Docker publishing workflow

## Session Notes
- 2026-05-11: v0.1.0 tagged — infra release (group rename to io.github.unityinflow, fat jar + GHCR Docker publishing). No feature changes; Phase 2 features retargeted to v0.2.0.
- 2026-04-02: All features already implemented. Added GSD planning, tagged release.

---
*Last updated: 2026-06-12*
