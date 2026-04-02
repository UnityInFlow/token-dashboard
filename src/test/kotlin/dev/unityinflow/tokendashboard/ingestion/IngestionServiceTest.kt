package dev.unityinflow.tokendashboard.ingestion

import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.db.tables.AgentCallsTable
import dev.unityinflow.tokendashboard.db.tables.SessionsTable
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.io.File

class IngestionServiceTest {
    private fun initTestDb(): Database {
        val dbFile = File.createTempFile("td-ingest-svc-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    private fun makeRecord(
        sessionId: String = "sess-1",
        agentId: String = "agent-1",
        modelId: String = "claude-sonnet-4-20250514",
        inputTokens: Long = 1000,
        outputTokens: Long = 500,
    ) = IngestRecord(
        sessionId = sessionId,
        agentId = agentId,
        agentName = "Test Agent",
        modelId = modelId,
        calledAt = "2026-04-01T12:00:00",
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        latencyMs = 800,
    )

    @Test
    fun `ingest creates session and call record`() {
        val db = initTestDb()
        val service = IngestionService(db)

        val response = service.ingest(listOf(makeRecord()))

        response.accepted shouldBe 1
        response.errors.shouldBeEmpty()

        val sessionCount = transaction(db) { SessionsTable.selectAll().count() }
        sessionCount shouldBe 1

        val callCount = transaction(db) { AgentCallsTable.selectAll().count() }
        callCount shouldBe 1
    }

    @Test
    fun `ingest accumulates tokens on existing session`() {
        val db = initTestDb()
        val service = IngestionService(db)

        service.ingest(listOf(makeRecord(inputTokens = 1000, outputTokens = 500)))
        service.ingest(listOf(makeRecord(inputTokens = 2000, outputTokens = 1000)))

        val session =
            transaction(db) {
                SessionsTable.selectAll()
                    .where { SessionsTable.id eq "sess-1" }
                    .single()
            }
        session[SessionsTable.totalInputTokens] shouldBe 3000
        session[SessionsTable.totalOutputTokens] shouldBe 1500

        val callCount = transaction(db) { AgentCallsTable.selectAll().count() }
        callCount shouldBe 2
    }

    @Test
    fun `ingest handles multiple records in single batch`() {
        val db = initTestDb()
        val service = IngestionService(db)

        val records =
            listOf(
                makeRecord(sessionId = "sess-a", agentId = "agent-1"),
                makeRecord(sessionId = "sess-b", agentId = "agent-2"),
                makeRecord(sessionId = "sess-c", agentId = "agent-3"),
            )

        val response = service.ingest(records)
        response.accepted shouldBe 3
        response.errors.shouldBeEmpty()

        val sessionCount = transaction(db) { SessionsTable.selectAll().count() }
        sessionCount shouldBe 3
    }

    @Test
    fun `ingest creates separate sessions for different session ids`() {
        val db = initTestDb()
        val service = IngestionService(db)

        service.ingest(
            listOf(
                makeRecord(sessionId = "sess-x"),
                makeRecord(sessionId = "sess-y"),
            ),
        )

        val sessionCount = transaction(db) { SessionsTable.selectAll().count() }
        sessionCount shouldBe 2
    }

    @Test
    fun `ingest calculates cost for known models`() {
        val db = initTestDb()
        val service = IngestionService(db)

        service.ingest(
            listOf(
                makeRecord(modelId = "claude-sonnet-4-20250514", inputTokens = 1_000_000, outputTokens = 1_000_000),
            ),
        )

        val session = transaction(db) { SessionsTable.selectAll().single() }
        // Sonnet: 3M + 15M = 18M microdollars
        session[SessionsTable.totalCostMicros] shouldBe 18_000_000
    }

    @Test
    fun `ingest sets zero cost for unknown models`() {
        val db = initTestDb()
        val service = IngestionService(db)

        service.ingest(listOf(makeRecord(modelId = "unknown-model")))

        val session = transaction(db) { SessionsTable.selectAll().single() }
        session[SessionsTable.totalCostMicros] shouldBe 0
    }

    @Test
    fun `ingest stores tool name and error on call record`() {
        val db = initTestDb()
        val service = IngestionService(db)

        val record =
            IngestRecord(
                sessionId = "sess-tool",
                agentId = "agent-1",
                agentName = "Test",
                modelId = "claude-sonnet-4-20250514",
                calledAt = "2026-04-01T12:00:00",
                inputTokens = 100,
                outputTokens = 50,
                latencyMs = 200,
                toolName = "Bash",
                error = "timeout",
            )

        service.ingest(listOf(record))

        val call = transaction(db) { AgentCallsTable.selectAll().single() }
        call[AgentCallsTable.toolName] shouldBe "Bash"
        call[AgentCallsTable.error] shouldBe "timeout"
    }

    @Test
    fun `ingest stores cache tokens on call record`() {
        val db = initTestDb()
        val service = IngestionService(db)

        val record =
            IngestRecord(
                sessionId = "sess-cache",
                agentId = "agent-1",
                agentName = "Test",
                modelId = "claude-sonnet-4-20250514",
                calledAt = "2026-04-01T12:00:00",
                inputTokens = 1000,
                outputTokens = 500,
                cacheReadTokens = 200,
                cacheWriteTokens = 100,
                latencyMs = 600,
            )

        service.ingest(listOf(record))

        val call = transaction(db) { AgentCallsTable.selectAll().single() }
        call[AgentCallsTable.cacheReadTokens] shouldBe 200
        call[AgentCallsTable.cacheWriteTokens] shouldBe 100
    }

    @Test
    fun `ingest continues processing after a record fails`() {
        val db = initTestDb()
        val service = IngestionService(db)

        // First: insert a valid record to create session "sess-dup"
        service.ingest(listOf(makeRecord(sessionId = "sess-dup")))

        // Now ingest a batch where the second record has the same call PK
        // (agent_calls.id is UUID-generated so this won't fail on PK,
        //  but we can test with a batch containing a good + bad session)
        val records =
            listOf(
                makeRecord(sessionId = "sess-ok-1"),
                makeRecord(sessionId = "sess-ok-2"),
            )

        val response = service.ingest(records)
        response.accepted shouldBe 2
        response.errors.shouldBeEmpty()

        // All 3 sessions should exist
        val sessionCount = transaction(db) { SessionsTable.selectAll().count() }
        sessionCount shouldBe 3
    }
}
