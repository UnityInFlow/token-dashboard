# token-dashboard

Real-time token usage and cost tracking dashboard for AI agent workflows. Ingests OpenTelemetry metrics, calculates per-model costs, and surfaces anomalies through an HTMX-powered UI.

Part of the [UnityInFlow](https://github.com/UnityInFlow) ecosystem.

## Features

- **Session tracking** — monitor agent coding sessions with per-call token breakdowns
- **Cost calculation** — automatic cost computation using configurable per-model pricing
- **HTMX dashboard** — server-rendered UI with live polling, no JS build step required
- **Anomaly detection** — Z-score based spike detection on token usage patterns
- **Budget alerts** — configurable thresholds with webhook dispatch (Slack, HTTP)
- **OTLP ingestion** — receive metrics via OpenTelemetry gRPC (port 4317)
- **REST API** — full CRUD for sessions, agents, costs, and alerts

## Quick Start

### Docker (recommended)

```bash
docker run -d \
  --name token-dashboard \
  -p 8080:8080 \
  -p 4317:4317 \
  -v dashboard-data:/data \
  ghcr.io/unityinflow/token-dashboard:latest
```

Open http://localhost:8080 to view the dashboard.

### Docker Compose

```bash
git clone https://github.com/UnityInFlow/token-dashboard.git
cd token-dashboard
docker compose up -d
```

### Standalone JAR

Requires JDK 21+.

```bash
./gradlew shadowJar
java -jar build/libs/token-dashboard-*-all.jar
```

## Configuration

| Variable | Default | Description |
|---|---|---|
| `TD_HOST` | `0.0.0.0` | Bind address |
| `TD_PORT` | `8080` | HTTP port |
| `TD_OTLP_PORT` | `4317` | OTLP gRPC receiver port |
| `TD_DB_PATH` | `token-dashboard.db` | SQLite database file path |

## API Examples

### Ingest a session with calls

```bash
curl -X POST http://localhost:8080/api/v1/sessions \
  -H 'Content-Type: application/json' \
  -d '{
    "agentId": "claude-coder-1",
    "agentName": "Claude Coder",
    "calls": [{
      "modelId": "claude-sonnet-4-20250514",
      "inputTokens": 1500,
      "outputTokens": 500,
      "cacheReadTokens": 200,
      "latencyMs": 850
    }]
  }'
```

### Check health

```bash
curl http://localhost:8080/api/v1/health
# {"status":"healthy","database":"connected"}
```

### List sessions

```bash
curl http://localhost:8080/api/v1/sessions
```

### Get cost summary

```bash
curl http://localhost:8080/api/v1/costs/summary
```

## Development

```bash
# Run tests
./gradlew test

# Build shadow JAR
./gradlew shadowJar

# Format with ktlint
./gradlew ktlintFormat

# Build Docker image locally
docker build -t token-dashboard .
```

## Stack

- **Runtime:** Kotlin 2.1 / JVM 21 / Ktor 3.x
- **Frontend:** HTMX + Pico CSS + Chart.js
- **Database:** SQLite via Exposed ORM
- **Metrics:** OpenTelemetry SDK + gRPC receiver
- **Build:** Gradle (Kotlin DSL) + Shadow plugin

## License

MIT
