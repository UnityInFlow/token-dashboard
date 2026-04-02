# Contributing to token-dashboard

Thank you for your interest in contributing to the token-dashboard project!

## Prerequisites

- JDK 21+
- Gradle (wrapper included — use `./gradlew`)

## Development Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/UnityInFlow/token-dashboard.git
   cd token-dashboard
   ```

2. Build the project:
   ```bash
   ./gradlew build
   ```

3. Run tests:
   ```bash
   ./gradlew test
   ```

4. Start locally:
   ```bash
   ./gradlew run
   ```
   The dashboard will be available at `http://localhost:8080`.

## Coding Standards

- **Kotlin 2.0+** with JVM target 21
- **Immutable by default** — use `val`, never `var`
- **No `!!` assertions** — use Elvis operator (`?:`) or safe calls (`?.`)
- **Sealed classes** for domain modelling (results, errors, states)
- **Coroutines** for all async work — never `Thread.sleep()` or raw threads
- Format with `ktlint` before every commit:
  ```bash
  ./gradlew ktlintFormat
  ```

## Testing

- Use **JUnit 5** with **Kotest matchers**
- Test coverage must be >80% on core logic
- Use Ktor test host for HTTP endpoint tests
- Name tests descriptively:
  ```kotlin
  @Test
  fun `sessions page returns HTML with Sessions heading`() { ... }
  ```

## Pull Request Process

1. Create a feature branch from `main`:
   ```bash
   git checkout -b feat/your-feature
   ```
2. Make your changes with clear, atomic commits
3. Ensure all tests pass: `./gradlew test`
4. Ensure code is formatted: `./gradlew ktlintCheck`
5. Open a PR against `main` with a clear description

## Commit Messages

Follow conventional commits:
```
feat: add per-model cost breakdown page
fix: correct burn rate calculation for idle sessions
test: add edge cases for alert evaluation
docs: update README with Docker instructions
chore: bump Ktor to 3.0.4
refactor: extract cost formatting to shared utility
```

## Architecture

- **Ktor** — HTTP server and HTML rendering
- **Exposed** — SQLite ORM
- **HTMX** — frontend interactivity without a JS framework
- **Pico CSS** — lightweight styling
- **OpenTelemetry** — OTLP metric ingestion via gRPC

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
