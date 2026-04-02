package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.configureAppWithDb
import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CostRoutesTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-cost-rt-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    private val now: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private fun seedSessionsAndCalls(db: Database) {
        val recentTime = LocalDateTime.now().minusMinutes(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        transaction(db) {
            SessionsTable.insert {
                it[id] = "sess-1"
                it[agentId] = "agent-a"
                it[agentName] = "Agent A"
                it[startedAt] = now
                it[totalInputTokens] = 5000
                it[totalOutputTokens] = 2000
                it[totalCostMicros] = 90000
            }
            SessionsTable.insert {
                it[id] = "sess-2"
                it[agentId] = "agent-b"
                it[agentName] = "Agent B"
                it[startedAt] = now
                it[endedAt] = now
                it[totalInputTokens] = 3000
                it[totalOutputTokens] = 1000
                it[totalCostMicros] = 60000
            }
            AgentCallsTable.insert {
                it[id] = "call-1"
                it[sessionId] = "sess-1"
                it[modelId] = "claude-sonnet-4-20250514"
                it[calledAt] = recentTime
                it[inputTokens] = 5000
                it[outputTokens] = 2000
                it[latencyMs] = 1200
                it[costMicros] = 90000
            }
            AgentCallsTable.insert {
                it[id] = "call-2"
                it[sessionId] = "sess-2"
                it[modelId] = "claude-haiku-4-5-20251001"
                it[calledAt] = recentTime
                it[inputTokens] = 3000
                it[outputTokens] = 1000
                it[latencyMs] = 400
                it[costMicros] = 60000
            }
        }
    }

    @Test
    fun `cost summary returns zeros when no data exists`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/costs/summary")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "todayMicros"
            body shouldContain "totalSessions"
        }

    @Test
    fun `cost summary returns correct aggregates for today`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedSessionsAndCalls(db)

            val response = client.get("/api/v1/costs/summary")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            // Both sessions started today so todayMicros should be 150000
            body shouldContain "150000"
            // Whitespace-agnostic JSON assertions
            val compact = body.replace("\\s".toRegex(), "")
            // 1 active session (sess-1 has no endedAt)
            compact shouldContain "\"activeSessions\":1"
            compact shouldContain "\"totalSessions\":2"
        }

    @Test
    fun `cost by model returns breakdown per model`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedSessionsAndCalls(db)

            val response = client.get("/api/v1/costs/by-model")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "claude-sonnet-4-20250514"
            body shouldContain "claude-haiku-4-5-20251001"
        }

    @Test
    fun `cost by model returns empty list when no calls exist`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/costs/by-model")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().replace(" ", "") shouldBe "[]"
        }

    @Test
    fun `cost by agent returns breakdown per agent`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedSessionsAndCalls(db)

            val response = client.get("/api/v1/costs/by-agent")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "agent-a"
            body shouldContain "Agent A"
            body shouldContain "agent-b"
        }

    @Test
    fun `cost by agent includes session count and avg cost`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedSessionsAndCalls(db)

            val response = client.get("/api/v1/costs/by-agent")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "sessionCount"
            body shouldContain "avgSessionCostMicros"
        }

    @Test
    fun `cost timeseries returns daily cost points`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedSessionsAndCalls(db)

            val response = client.get("/api/v1/costs/timeseries")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            // Should have at least one data point for today
            body shouldContain "costMicros"
            body shouldContain "callCount"
        }

    @Test
    fun `cost timeseries days parameter controls query window`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            // Seed a session 10 days ago
            val tenDaysAgo = LocalDateTime.now().minusDays(10).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            transaction(db) {
                SessionsTable.insert {
                    it[id] = "sess-old"
                    it[agentId] = "agent-old"
                    it[agentName] = "Old Agent"
                    it[startedAt] = tenDaysAgo
                    it[totalCostMicros] = 50000
                }
            }

            // days=30 should include the 10-day-old session
            val wide = client.get("/api/v1/costs/timeseries?days=30")
            wide.status shouldBe HttpStatusCode.OK
            wide.bodyAsText() shouldContain "50000"

            // days=3 should NOT include the 10-day-old session
            val narrow = client.get("/api/v1/costs/timeseries?days=3")
            narrow.status shouldBe HttpStatusCode.OK
            narrow.bodyAsText() shouldNotContain "50000"
        }

    @Test
    fun `cost timeseries returns empty list when no sessions in range`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/costs/timeseries?days=1")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().replace(" ", "") shouldBe "[]"
        }

    @Test
    fun `burn rate returns rate metrics`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedSessionsAndCalls(db)

            val response = client.get("/api/v1/costs/burn-rate")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "tokensPerMinute"
            body shouldContain "projectedSessionCostMicros"
            body shouldContain "activeSessions"
            body shouldContain "windowMinutes"
        }

    @Test
    fun `burn rate returns zeros when no recent calls`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/costs/burn-rate")
            response.status shouldBe HttpStatusCode.OK
            val compact = response.bodyAsText().replace("\\s".toRegex(), "")
            compact shouldContain "\"tokensPerMinute\":0.0"
            compact shouldContain "\"activeSessions\":0"
        }

    @Test
    fun `burn rate accepts window parameter`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/costs/burn-rate?window=10")
            response.status shouldBe HttpStatusCode.OK
            val compact = response.bodyAsText().replace("\\s".toRegex(), "")
            compact shouldContain "\"windowMinutes\":10"
        }
}
