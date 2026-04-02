package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.configureAppWithDb
import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.BudgetAlertsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

class HtmxFragmentsTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-htmx-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    private val recentTime: String =
        LocalDateTime.now().minusMinutes(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private fun seedData(db: Database) {
        transaction(db) {
            SessionsTable.insert {
                it[id] = "sess-1"
                it[agentId] = "agent-alpha"
                it[agentName] = "Alpha Agent"
                it[startedAt] = recentTime
                it[totalInputTokens] = 5000
                it[totalOutputTokens] = 2000
                it[totalCostMicros] = 90000
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
        }
    }

    @Test
    fun `sessions-table fragment returns HTML with session data`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedData(db)

            val response = client.get("/htmx/sessions-table")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "Alpha Agent"
        }

    @Test
    fun `sessions-table fragment returns HTML for empty database`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/htmx/sessions-table")
            response.status shouldBe HttpStatusCode.OK
        }

    @Test
    fun `agents-grid fragment returns HTML with agent data`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedData(db)

            val response = client.get("/htmx/agents-grid")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "Alpha Agent"
        }

    @Test
    fun `agents-grid fragment returns HTML for empty database`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/htmx/agents-grid")
            response.status shouldBe HttpStatusCode.OK
        }

    @Test
    fun `alerts-table fragment returns HTML with alert data`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            transaction(db) {
                BudgetAlertsTable.insert {
                    it[id] = "alert-1"
                    it[name] = "High Cost"
                    it[thresholdMicros] = 1_000_000
                    it[period] = "DAILY"
                    it[enabled] = true
                    it[createdAt] = "2026-04-01T10:00:00"
                }
            }

            val response = client.get("/htmx/alerts-table")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "High Cost"
        }

    @Test
    fun `alerts-table fragment returns HTML for empty database`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/htmx/alerts-table")
            response.status shouldBe HttpStatusCode.OK
        }

    @Test
    fun `cost-chart-data fragment returns HTML with chart script`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedData(db)

            val response = client.get("/htmx/cost-chart-data")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "costChart"
        }

    @Test
    fun `burn-rate fragment returns HTML with rate data`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedData(db)

            val response = client.get("/htmx/burn-rate")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "tok/min"
        }

    @Test
    fun `burn-rate fragment returns zeros for empty database`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/htmx/burn-rate")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "0"
        }
}
