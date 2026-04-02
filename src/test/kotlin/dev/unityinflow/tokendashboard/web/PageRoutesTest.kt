package dev.unityinflow.tokendashboard.web

import dev.unityinflow.tokendashboard.configureAppWithDb
import dev.unityinflow.tokendashboard.db.DatabaseFactory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.io.File

class PageRoutesTest {
    private fun createTestDb(): org.jetbrains.exposed.sql.Database {
        val dbFile = File.createTempFile("td-pages-", ".db")
        dbFile.deleteOnExit()
        return DatabaseFactory.init(dbFile.absolutePath)
    }

    @Test
    fun `dashboard page returns HTML with Dashboard heading`() =
        testApplication {
            val db = createTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "Dashboard"
            body shouldContain "Today"
            body shouldContain "This Week"
            body shouldContain "This Month"
        }

    @Test
    fun `sessions page returns HTML with Sessions heading`() =
        testApplication {
            val db = createTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/sessions")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "Sessions"
        }

    @Test
    fun `agents page returns HTML with Agents heading`() =
        testApplication {
            val db = createTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/agents")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "Agents"
        }

    @Test
    fun `alerts page returns HTML with Alerts heading`() =
        testApplication {
            val db = createTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/alerts")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "Alerts"
        }

    @Test
    fun `models page returns HTML with Models heading`() =
        testApplication {
            val db = createTestDb()
            application { configureAppWithDb(db) }

            val response = client.get("/models")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "Models"
        }
}
