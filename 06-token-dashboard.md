# Tool 06: `token-dashboard`
## Real-Time Token Burn Dashboard — Deep Dive

> **Phase:** 2 · **Effort:** 4/10 · **Impact:** 7/10 · **Stack:** Kotlin (Ktor) + HTMX  
> **Repo name:** `token-dashboard` · **Build in:** Weeks 10–12

---

## 1. Problem Statement

RTK compresses CLI output but provides only basic ASCII analytics. Teams running overnight agents need a real-time dashboard: burn rate per agent, cost forecast, anomaly detection, and budget alerts. The data exists (budget-breaker emits Micrometer metrics) but no purpose-built UI consumes it.

---

## 2. Key Features — v0.1 Checklist

- [ ] Real-time burn rate display: tokens/minute, projected session cost
- [ ] Per-agent cost breakdown with sortable table
- [ ] Per-model breakdown: compare Opus vs Sonnet vs Haiku costs
- [ ] Session history: cost trends over last 30 days
- [ ] Anomaly detection: flag agents running 2x+ normal cost
- [ ] Budget alert webhooks: Slack and custom URL on threshold breach
- [ ] SQLite backend: local-first, zero cloud dependency
- [ ] HTMX frontend: live updates without JavaScript framework
- [ ] OTLP receiver: ingests metrics from budget-breaker and KORE
- [ ] Docker one-liner: \`docker run -p 8080:8080 your-org/token-dashboard\`
- [ ] REST API for integration with other tools

---

## 3. Technical Stack

**Language:** Kotlin (Ktor server)  
**Frontend:** HTMX + minimal CSS (no JavaScript framework)  
**Storage:** SQLite via Exposed ORM  
**Metrics ingestion:** OpenTelemetry OTLP receiver  
**Distribution:** Docker image (`your-org/token-dashboard`) + standalone JAR  

**Key design decisions:**  
- HTMX means zero JavaScript build step — the dashboard works from day one  
- SQLite means zero setup — no database server required for local use  
- Ktor is lighter than Spring WebFlux for simple dashboard serving  
- OTLP receiver means any OTel-compatible tool can feed data into it

---

## 4. Key Implementation Todos

### Week 10: Ktor Server + Storage
- [ ] Ktor server project setup with build-conventions plugin
- [ ] SQLite schema: sessions, agent_calls, model_costs, budget_alerts tables
- [ ] OTLP receiver: accept metrics from budget-breaker via gRPC
- [ ] REST API: GET /api/sessions, /api/agents, /api/alerts endpoints

### Week 11: HTMX Dashboard
- [ ] Dashboard home: live burn rate, active sessions, today total cost
- [ ] Sessions page: history table with cost per session
- [ ] Agents page: per-agent cost breakdown
- [ ] HTMX polling: 3-second refresh without page reload
- [ ] Slack webhook integration

### Week 12: Docker + Release
- [ ] Multi-stage Dockerfile (final image <50MB)
- [ ] docker-compose.yml with SQLite volume mount
- [ ] Publish to Docker Hub: your-org/token-dashboard:latest
- [ ] Blog post: "Real-time AI agent cost monitoring with HTMX and Kotlin"

---

## 5. Success Metrics

| Metric | First Release Target | 3 Month Target |
|---|---|---|
| GitHub stars | 50 | 250 |
| Docker pulls | 30 | 500 |
| Active dashboard users | 5 | 50 |

---

*Part of the AI Agent Tooling Ecosystem · See 00-MASTER-ANALYSIS.md for full context*
