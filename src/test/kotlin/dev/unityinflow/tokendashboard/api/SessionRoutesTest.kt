package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.configureAppWithDb
import dev.unityinflow.tokendashboard.db.DatabaseFactory
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

class SessionRoutesTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-session-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    @Test
    fun `sessions endpoint returns empty list when no sessions exist`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/sessions")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().replace(" ", "") shouldBe "[]"
        }

    @Test
    fun `sessions endpoint returns sessions after insert`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            transaction(db) {
                SessionsTable.insert {
                    it[id] = "sess-1"
                    it[agentId] = "agent-1"
                    it[agentName] = "TestAgent"
                    it[startedAt] = "2026-04-01T10:00:00"
                    it[totalInputTokens] = 1000
                    it[totalOutputTokens] = 500
                    it[totalCostMicros] = 15000
                }
            }

            val response = client.get("/api/v1/sessions")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "sess-1"
            response.bodyAsText() shouldContain "TestAgent"
        }

    @Test
    fun `active sessions returns only sessions without ended_at`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            transaction(db) {
                SessionsTable.insert {
                    it[id] = "active-1"
                    it[agentId] = "agent-1"
                    it[agentName] = "Active"
                    it[startedAt] = "2026-04-01T10:00:00"
                }
                SessionsTable.insert {
                    it[id] = "ended-1"
                    it[agentId] = "agent-1"
                    it[agentName] = "Ended"
                    it[startedAt] = "2026-04-01T09:00:00"
                    it[endedAt] = "2026-04-01T09:30:00"
                }
            }

            val response = client.get("/api/v1/sessions/active")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "active-1"
        }

    @Test
    fun `session by id returns 404 for non-existent session`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/sessions/non-existent")
            response.status shouldBe HttpStatusCode.NotFound
        }
}
