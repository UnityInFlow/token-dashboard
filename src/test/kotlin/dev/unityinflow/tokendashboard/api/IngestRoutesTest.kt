package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.configureAppWithDb
import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.io.File

class IngestRoutesTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-ingest-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    @Test
    fun `ingest endpoint accepts valid records and creates session + call`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response =
                client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "records": [{
                                "sessionId": "sess-100",
                                "agentId": "agent-a",
                                "agentName": "TestAgent",
                                "modelId": "claude-sonnet-4-20250514",
                                "calledAt": "2026-04-01T12:00:00",
                                "inputTokens": 1000,
                                "outputTokens": 500,
                                "latencyMs": 1200
                            }]
                        }
                        """.trimIndent(),
                    )
                }

            response.status shouldBe HttpStatusCode.Accepted
            response.bodyAsText() shouldContain "\"accepted\": 1"

            // Verify session was created
            val sessionCount = transaction(db) { SessionsTable.selectAll().count() }
            sessionCount shouldBe 1

            // Verify call was created
            val callCount = transaction(db) { AgentCallsTable.selectAll().count() }
            callCount shouldBe 1
        }

    @Test
    fun `ingest endpoint accumulates tokens on existing session`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            // First ingest
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"records": [{
                        "sessionId": "sess-200",
                        "agentId": "agent-b",
                        "agentName": "Agent B",
                        "modelId": "claude-sonnet-4-20250514",
                        "calledAt": "2026-04-01T12:00:00",
                        "inputTokens": 1000,
                        "outputTokens": 500,
                        "latencyMs": 800
                    }]}
                    """.trimIndent(),
                )
            }

            // Second ingest for same session
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"records": [{
                        "sessionId": "sess-200",
                        "agentId": "agent-b",
                        "agentName": "Agent B",
                        "modelId": "claude-sonnet-4-20250514",
                        "calledAt": "2026-04-01T12:01:00",
                        "inputTokens": 2000,
                        "outputTokens": 1000,
                        "latencyMs": 900
                    }]}
                    """.trimIndent(),
                )
            }

            // Should have 1 session with accumulated tokens
            val session =
                transaction(db) {
                    SessionsTable.selectAll()
                        .where { SessionsTable.id eq "sess-200" }
                        .single()
                }
            session[SessionsTable.totalInputTokens] shouldBe 3000
            session[SessionsTable.totalOutputTokens] shouldBe 1500

            // Should have 2 call records
            val callCount = transaction(db) { AgentCallsTable.selectAll().count() }
            callCount shouldBe 2
        }

    @Test
    fun `ingest endpoint rejects empty records list`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response =
                client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"records": []}""")
                }

            response.status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `ingested data appears in sessions API`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"records": [{
                        "sessionId": "sess-300",
                        "agentId": "agent-c",
                        "agentName": "Visible Agent",
                        "modelId": "claude-haiku-4-5-20251001",
                        "calledAt": "2026-04-01T14:00:00",
                        "inputTokens": 500,
                        "outputTokens": 200,
                        "latencyMs": 300,
                        "toolName": "Read"
                    }]}
                    """.trimIndent(),
                )
            }

            val response = client.get("/api/v1/sessions")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "sess-300"
            response.bodyAsText() shouldContain "Visible Agent"
        }
}
