package dev.unityinflow.tokendashboard.api

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

class HealthRoutesTest {
    @Test
    fun `health endpoint returns healthy when database is connected`() =
        testApplication {
            val dbFile = File.createTempFile("td-health-", ".db")
            dbFile.deleteOnExit()
            val db = DatabaseFactory.init(dbFile.absolutePath)

            application { configureAppWithDb(db) }

            val response = client.get("/api/v1/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "healthy"
            response.bodyAsText() shouldContain "connected"
        }
}
