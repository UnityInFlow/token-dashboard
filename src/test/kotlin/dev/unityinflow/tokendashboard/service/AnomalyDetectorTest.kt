package dev.unityinflow.tokendashboard.service

import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import dev.unityinflow.tokendashboard.domain.AlertPeriod
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AnomalyDetectorTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-anomaly-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    private fun insertSession(
        db: Database,
        id: String,
        agentId: String,
        costMicros: Long,
        startedAt: String,
    ) {
        transaction(db) {
            SessionsTable.insert {
                it[SessionsTable.id] = id
                it[SessionsTable.agentId] = agentId
                it[agentName] = "Test Agent"
                it[SessionsTable.startedAt] = startedAt
                it[totalCostMicros] = costMicros
            }
        }
    }

    @Test
    fun `detects anomaly when current cost exceeds 2x rolling average`() {
        val db = initTestDb()
        val detector = AnomalyDetector(multiplierThreshold = 2.0)
        val now = LocalDateTime.of(2026, 4, 1, 15, 0, 0)

        // Insert sessions for previous days with ~100k each
        for (i in 4 downTo 1) {
            val day = now.minusDays(i.toLong()).toLocalDate().atStartOfDay().plusHours(10)
            insertSession(db, "s-prev-$i", "agent-a", 100_000L, day.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        }

        // Insert current day session with 300k (3x average = anomaly)
        val today = now.toLocalDate().atStartOfDay().plusHours(10)
        insertSession(db, "s-today", "agent-a", 300_000L, today.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

        val result = detector.detectAnomaly(db, AlertPeriod.DAILY, null, now)

        result.shouldNotBeNull()
        result.multiplier shouldBeGreaterThan 2.0
        result.currentCostMicros shouldBe 300_000L
    }

    @Test
    fun `returns null when cost is within normal range`() {
        val db = initTestDb()
        val detector = AnomalyDetector(multiplierThreshold = 2.0)
        val now = LocalDateTime.of(2026, 4, 1, 15, 0, 0)

        // Insert previous days with ~100k
        for (i in 4 downTo 1) {
            val day = now.minusDays(i.toLong()).toLocalDate().atStartOfDay().plusHours(10)
            insertSession(db, "s-prev-$i", "agent-a", 100_000L, day.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        }

        // Insert current day with 120k (only 1.2x, below 2.0 threshold)
        val today = now.toLocalDate().atStartOfDay().plusHours(10)
        insertSession(db, "s-today", "agent-a", 120_000L, today.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

        val result = detector.detectAnomaly(db, AlertPeriod.DAILY, null, now)

        result.shouldBeNull()
    }

    @Test
    fun `returns null when no previous data exists`() {
        val db = initTestDb()
        val detector = AnomalyDetector()
        val now = LocalDateTime.of(2026, 4, 1, 15, 0, 0)

        // Only current day data
        val today = now.toLocalDate().atStartOfDay().plusHours(10)
        insertSession(db, "s-today", "agent-a", 500_000L, today.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

        val result = detector.detectAnomaly(db, AlertPeriod.DAILY, null, now)

        result.shouldBeNull()
    }

    @Test
    fun `returns null for SESSION period`() {
        val db = initTestDb()
        val detector = AnomalyDetector()

        val result = detector.detectAnomaly(db, AlertPeriod.SESSION, null)

        result.shouldBeNull()
    }

    @Test
    fun `filters by agentId when specified`() {
        val db = initTestDb()
        val detector = AnomalyDetector(multiplierThreshold = 2.0)
        val now = LocalDateTime.of(2026, 4, 5, 15, 0, 0)

        // Insert previous days with high cost for agent-a, low for agent-b
        for (i in 4 downTo 1) {
            val day = now.minusDays(i.toLong()).toLocalDate().atStartOfDay().plusHours(10)
            insertSession(db, "s-a-$i", "agent-a", 100_000L, day.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            insertSession(db, "s-b-$i", "agent-b", 10_000L, day.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        }

        // Current day: agent-b spends 50k (5x its average of 10k)
        val today = now.toLocalDate().atStartOfDay().plusHours(10)
        insertSession(db, "s-b-today", "agent-b", 50_000L, today.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

        val result = detector.detectAnomaly(db, AlertPeriod.DAILY, "agent-b", now)

        result.shouldNotBeNull()
        result.multiplier shouldBeGreaterThan 2.0
    }

    @Test
    fun `computePeriodStartAt returns start of day for DAILY`() {
        val detector = AnomalyDetector()
        val testTime = LocalDateTime.of(2026, 4, 1, 15, 30, 0)

        val result = detector.computePeriodStartAt(AlertPeriod.DAILY, testTime)

        result shouldBe "2026-04-01T00:00:00"
    }

    @Test
    fun `computePreviousPeriodStarts returns correct daily starts`() {
        val detector = AnomalyDetector()
        val testTime = LocalDateTime.of(2026, 4, 5, 12, 0, 0)

        val starts = detector.computePreviousPeriodStarts(AlertPeriod.DAILY, testTime, count = 3)

        starts.size shouldBe 4 // count + 1 boundaries
        starts[0] shouldBe "2026-04-02T00:00:00"
        starts[1] shouldBe "2026-04-03T00:00:00"
        starts[2] shouldBe "2026-04-04T00:00:00"
        starts[3] shouldBe "2026-04-05T00:00:00"
    }
}
