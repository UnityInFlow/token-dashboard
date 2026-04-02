package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.configureAppWithDb
import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.db.tables.AlertHistoryTable
import dev.unityinflow.tokendashboard.db.tables.BudgetAlertsTable
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.io.File

class AlertRoutesTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-alert-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    private fun seedAlert(
        db: Database,
        id: String = "alert-1",
        name: String = "High Cost Alert",
        agentId: String? = null,
        thresholdMicros: Long = 1_000_000,
        period: String = "DAILY",
        webhookUrl: String? = "https://hooks.example.com/test",
        enabled: Boolean = true,
    ) {
        transaction(db) {
            BudgetAlertsTable.insert {
                it[BudgetAlertsTable.id] = id
                it[BudgetAlertsTable.name] = name
                it[BudgetAlertsTable.agentId] = agentId
                it[BudgetAlertsTable.thresholdMicros] = thresholdMicros
                it[BudgetAlertsTable.period] = period
                it[BudgetAlertsTable.webhookUrl] = webhookUrl
                it[BudgetAlertsTable.enabled] = enabled
                it[BudgetAlertsTable.createdAt] = "2026-04-01T10:00:00"
            }
        }
    }

    @Test
    fun `list alerts returns empty list when no alerts exist`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/alerts")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().replace(" ", "") shouldBe "[]"
        }

    @Test
    fun `list alerts returns existing alerts`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedAlert(db)

            val response = client.get("/api/v1/alerts")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "High Cost Alert"
            body shouldContain "DAILY"
        }

    @Test
    fun `create alert returns 201 with created alert`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response =
                client.post("/api/v1/alerts") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "Budget Limit",
                            "thresholdMicros": 5000000,
                            "period": "WEEKLY",
                            "webhookUrl": "https://hooks.slack.com/test"
                        }
                        """.trimIndent(),
                    )
                }

            response.status shouldBe HttpStatusCode.Created
            val body = response.bodyAsText()
            body shouldContain "Budget Limit"
            body shouldContain "5000000"
            body shouldContain "WEEKLY"
            body shouldContain "https://hooks.slack.com/test"
        }

    @Test
    fun `create alert with agent filter`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response =
                client.post("/api/v1/alerts") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "Agent Alert",
                            "agentId": "agent-123",
                            "thresholdMicros": 2000000,
                            "period": "DAILY"
                        }
                        """.trimIndent(),
                    )
                }

            response.status shouldBe HttpStatusCode.Created
            val body = response.bodyAsText()
            body shouldContain "agent-123"
        }

    @Test
    fun `update alert modifies existing alert`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedAlert(db)

            val response =
                client.patch("/api/v1/alerts/alert-1") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "Updated Alert Name",
                            "thresholdMicros": 2000000,
                            "enabled": false
                        }
                        """.trimIndent(),
                    )
                }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "Updated Alert Name"
            body shouldContain "2000000"
        }

    @Test
    fun `update alert returns 404 for non-existent alert`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response =
                client.patch("/api/v1/alerts/non-existent") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "Updated"}""")
                }

            response.status shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `delete alert removes existing alert`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedAlert(db)

            val response = client.delete("/api/v1/alerts/alert-1")
            response.status shouldBe HttpStatusCode.NoContent

            // Verify it's gone
            val listResponse = client.get("/api/v1/alerts")
            listResponse.bodyAsText() shouldNotContain "High Cost Alert"
        }

    @Test
    fun `delete alert returns 404 for non-existent alert`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.delete("/api/v1/alerts/non-existent")
            response.status shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `alert history returns empty list when no alerts have fired`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/alerts/history")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().replace(" ", "") shouldBe "[]"
        }

    @Test
    fun `alert history returns fired alert entries`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedAlert(db)

            transaction(db) {
                AlertHistoryTable.insert {
                    it[id] = "hist-1"
                    it[alertId] = "alert-1"
                    it[firedAt] = "2026-04-01T12:00:00"
                    it[costAtFireMicros] = 1_500_000
                    it[webhookStatus] = 200
                }
            }

            val response = client.get("/api/v1/alerts/history")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "hist-1"
            body shouldContain "1500000"
        }

    @Test
    fun `alert history filters by alertId query parameter`() =
        testApplication {
            val db = initTestDb()
            application { configureAppWithDb(db) }
            seedAlert(db, id = "alert-1")
            seedAlert(db, id = "alert-2", name = "Second Alert")

            transaction(db) {
                AlertHistoryTable.insert {
                    it[id] = "hist-1"
                    it[alertId] = "alert-1"
                    it[firedAt] = "2026-04-01T12:00:00"
                    it[costAtFireMicros] = 1_000_000
                }
                AlertHistoryTable.insert {
                    it[id] = "hist-2"
                    it[alertId] = "alert-2"
                    it[firedAt] = "2026-04-01T13:00:00"
                    it[costAtFireMicros] = 2_000_000
                }
            }

            val response = client.get("/api/v1/alerts/history?alertId=alert-1")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "hist-1"
            body shouldNotContain "hist-2"
        }
}
