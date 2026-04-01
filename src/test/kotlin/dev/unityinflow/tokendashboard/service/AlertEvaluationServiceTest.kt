package dev.unityinflow.tokendashboard.service

import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.db.tables.AlertHistoryTable
import dev.unityinflow.tokendashboard.db.tables.BudgetAlertsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.AlertPeriod
import dev.unityinflow.tokendashboard.webhook.WebhookDispatcher
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AlertEvaluationServiceTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-alert-eval-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    private fun createMockDispatcher(): WebhookDispatcher {
        val mockEngine =
            MockEngine {
                respond(
                    content = """{"ok":true}""",
                    status = HttpStatusCode.OK,
                )
            }
        return WebhookDispatcher(HttpClient(mockEngine))
    }

    private fun insertSession(
        db: Database,
        id: String,
        agentId: String,
        costMicros: Long,
        startedAt: String? = null,
    ) {
        val now = startedAt ?: LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        transaction(db) {
            SessionsTable.insert {
                it[SessionsTable.id] = id
                it[SessionsTable.agentId] = agentId
                it[agentName] = "Test Agent"
                it[SessionsTable.startedAt] = now
                it[totalCostMicros] = costMicros
            }
        }
    }

    private fun insertAlert(
        db: Database,
        id: String,
        name: String,
        thresholdMicros: Long,
        period: AlertPeriod,
        webhookUrl: String? = null,
        agentId: String? = null,
        enabled: Boolean = true,
    ) {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        transaction(db) {
            BudgetAlertsTable.insert {
                it[BudgetAlertsTable.id] = id
                it[BudgetAlertsTable.name] = name
                it[BudgetAlertsTable.agentId] = agentId
                it[BudgetAlertsTable.thresholdMicros] = thresholdMicros
                it[BudgetAlertsTable.period] = period.name
                it[BudgetAlertsTable.webhookUrl] = webhookUrl
                it[BudgetAlertsTable.enabled] = enabled
                it[createdAt] = now
            }
        }
    }

    @Test
    fun `fires alert when cost exceeds threshold`() =
        runTest {
            val db = initTestDb()
            val dispatcher = createMockDispatcher()
            val anomalyDetector = AnomalyDetector()
            val service = AlertEvaluationService(db, dispatcher, anomalyDetector)

            insertSession(db, "s1", "agent-a", 200_000L)
            insertAlert(db, "a1", "Daily budget", 100_000L, AlertPeriod.DAILY, "https://hooks.example.com/test")

            service.evaluateAlerts()

            val historyCount =
                transaction(db) {
                    AlertHistoryTable.selectAll().count()
                }
            historyCount shouldBe 1

            val entry =
                transaction(db) {
                    AlertHistoryTable.selectAll().single()
                }
            entry[AlertHistoryTable.alertId] shouldBe "a1"
            entry[AlertHistoryTable.costAtFireMicros] shouldBe 200_000L
            entry[AlertHistoryTable.webhookStatus] shouldBe 200
        }

    @Test
    fun `does not fire alert when cost is below threshold`() =
        runTest {
            val db = initTestDb()
            val dispatcher = createMockDispatcher()
            val anomalyDetector = AnomalyDetector()
            val service = AlertEvaluationService(db, dispatcher, anomalyDetector)

            insertSession(db, "s1", "agent-a", 50_000L)
            insertAlert(db, "a1", "Daily budget", 100_000L, AlertPeriod.DAILY)

            service.evaluateAlerts()

            val historyCount =
                transaction(db) {
                    AlertHistoryTable.selectAll().count()
                }
            historyCount shouldBe 0
        }

    @Test
    fun `does not fire alert twice in same period`() =
        runTest {
            val db = initTestDb()
            val dispatcher = createMockDispatcher()
            val anomalyDetector = AnomalyDetector()
            val service = AlertEvaluationService(db, dispatcher, anomalyDetector)

            insertSession(db, "s1", "agent-a", 200_000L)
            insertAlert(db, "a1", "Daily budget", 100_000L, AlertPeriod.DAILY)

            service.evaluateAlerts()
            service.evaluateAlerts()

            val historyCount =
                transaction(db) {
                    AlertHistoryTable.selectAll().count()
                }
            historyCount shouldBe 1
        }

    @Test
    fun `skips disabled alerts`() =
        runTest {
            val db = initTestDb()
            val dispatcher = createMockDispatcher()
            val anomalyDetector = AnomalyDetector()
            val service = AlertEvaluationService(db, dispatcher, anomalyDetector)

            insertSession(db, "s1", "agent-a", 200_000L)
            insertAlert(db, "a1", "Disabled alert", 100_000L, AlertPeriod.DAILY, enabled = false)

            service.evaluateAlerts()

            val historyCount =
                transaction(db) {
                    AlertHistoryTable.selectAll().count()
                }
            historyCount shouldBe 0
        }

    @Test
    fun `fires alert without webhook when webhookUrl is null`() =
        runTest {
            val db = initTestDb()
            val dispatcher = createMockDispatcher()
            val anomalyDetector = AnomalyDetector()
            val service = AlertEvaluationService(db, dispatcher, anomalyDetector)

            insertSession(db, "s1", "agent-a", 200_000L)
            insertAlert(db, "a1", "No webhook alert", 100_000L, AlertPeriod.DAILY, webhookUrl = null)

            service.evaluateAlerts()

            val entry =
                transaction(db) {
                    AlertHistoryTable.selectAll().single()
                }
            entry[AlertHistoryTable.webhookStatus] shouldBe null
            entry[AlertHistoryTable.costAtFireMicros] shouldBe 200_000L
        }

    @Test
    fun `filters by agentId when set on alert`() =
        runTest {
            val db = initTestDb()
            val dispatcher = createMockDispatcher()
            val anomalyDetector = AnomalyDetector()
            val service = AlertEvaluationService(db, dispatcher, anomalyDetector)

            insertSession(db, "s1", "agent-a", 200_000L)
            insertSession(db, "s2", "agent-b", 50_000L)
            insertAlert(db, "a1", "Agent B alert", 100_000L, AlertPeriod.DAILY, agentId = "agent-b")

            service.evaluateAlerts()

            val historyCount =
                transaction(db) {
                    AlertHistoryTable.selectAll().count()
                }
            historyCount shouldBe 0
        }

    @Test
    fun `computePeriodStart returns start of today for DAILY`() {
        val db = initTestDb()
        val dispatcher = createMockDispatcher()
        val anomalyDetector = AnomalyDetector()
        val service = AlertEvaluationService(db, dispatcher, anomalyDetector)

        val result = service.computePeriodStart(AlertPeriod.DAILY)
        val expected =
            LocalDateTime.now().toLocalDate().atStartOfDay()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        result shouldBe expected
    }
}
