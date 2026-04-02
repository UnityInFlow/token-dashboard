package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.configureAppWithDb
import dev.unityinflow.tokendashboard.db.DatabaseFactory
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

class AgentRoutesTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-agent-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    private fun seedAgentData(db: Database) {
        transaction(db) {
            SessionsTable.insert {
                it[id] = "sess-a1"
                it[agentId] = "agent-alpha"
                it[agentName] = "Alpha Agent"
                it[startedAt] = "2026-04-01T10:00:00"
                it[totalInputTokens] = 5000
                it[totalOutputTokens] = 2000
                it[totalCostMicros] = 90000
            }
            SessionsTable.insert {
                it[id] = "sess-a2"
                it[agentId] = "agent-alpha"
                it[agentName] = "Alpha Agent"
                it[startedAt] = "2026-04-01T11:00:00"
                it[totalInputTokens] = 3000
                it[totalOutputTokens] = 1000
                it[totalCostMicros] = 60000
            }
            SessionsTable.insert {
                it[id] = "sess-b1"
                it[agentId] = "agent-beta"
                it[agentName] = "Beta Agent"
                it[startedAt] = "2026-04-01T12:00:00"
                it[totalInputTokens] = 1000
                it[totalOutputTokens] = 500
                it[totalCostMicros] = 15000
            }
        }
    }

    @Test
    fun `agents endpoint returns empty list when no sessions exist`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/agents")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().replace(" ", "") shouldBe "[]"
        }

    @Test
    fun `agents endpoint returns aggregated agent cost breakdowns`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedAgentData(db)

            val response = client.get("/api/v1/agents")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "agent-alpha"
            body shouldContain "Alpha Agent"
            body shouldContain "agent-beta"
            body shouldContain "Beta Agent"
        }

    @Test
    fun `agents endpoint returns agents sorted by cost descending`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedAgentData(db)

            val response = client.get("/api/v1/agents")
            val body = response.bodyAsText()
            // Alpha has 150000 total cost, Beta has 15000 — Alpha should come first
            val alphaIdx = body.indexOf("agent-alpha")
            val betaIdx = body.indexOf("agent-beta")
            (alphaIdx < betaIdx) shouldBe true
        }

    @Test
    fun `agent by id returns cost breakdown for specific agent`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedAgentData(db)

            val response = client.get("/api/v1/agents/agent-alpha")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "agent-alpha"
            body shouldContain "Alpha Agent"
            body shouldContain "150000" // 90000 + 60000
        }

    @Test
    fun `agent by id returns 404 for non-existent agent`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/agents/non-existent")
            response.status shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `agent sessions returns sessions for specific agent`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedAgentData(db)

            val response = client.get("/api/v1/agents/agent-alpha/sessions")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "sess-a1"
            body shouldContain "sess-a2"
            body shouldNotContain "sess-b1"
        }

    @Test
    fun `agent sessions returns empty list for agent with no sessions`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/agents/no-such-agent/sessions")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().replace(" ", "") shouldBe "[]"
        }
}
