package dev.unityinflow.tokendashboard.webhook

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class WebhookDispatcherTest {
    @Test
    fun `dispatch sends POST with JSON payload and returns status`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = """{"ok":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = HttpClient(mockEngine)
            val dispatcher = WebhookDispatcher(client)
            val payload =
                WebhookPayload(
                    alertId = "alert-1",
                    alertName = "Daily budget",
                    agentId = "agent-a",
                    period = "DAILY",
                    thresholdMicros = 100_000L,
                    currentCostMicros = 150_000L,
                    firedAt = "2026-04-01T12:00:00",
                )

            val result = dispatcher.dispatch("https://hooks.example.com/webhook", payload)

            result.statusCode shouldBe 200
            result.responseBody shouldBe """{"ok":true}"""
        }

    @Test
    fun `dispatch returns -1 status on network failure`() =
        runTest {
            val mockEngine =
                MockEngine {
                    throw java.io.IOException("Connection refused")
                }

            val client = HttpClient(mockEngine)
            val dispatcher = WebhookDispatcher(client)
            val payload =
                WebhookPayload(
                    alertId = "alert-2",
                    alertName = "Monthly cap",
                    agentId = null,
                    period = "MONTHLY",
                    thresholdMicros = 500_000L,
                    currentCostMicros = 600_000L,
                    firedAt = "2026-04-01T12:00:00",
                )

            val result = dispatcher.dispatch("https://unreachable.example.com/webhook", payload)

            result.statusCode shouldBe -1
        }

    @Test
    fun `dispatch handles non-200 status codes`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = "Internal Server Error",
                        status = HttpStatusCode.InternalServerError,
                    )
                }

            val client = HttpClient(mockEngine)
            val dispatcher = WebhookDispatcher(client)
            val payload =
                WebhookPayload(
                    alertId = "alert-3",
                    alertName = "Weekly limit",
                    agentId = "agent-b",
                    period = "WEEKLY",
                    thresholdMicros = 200_000L,
                    currentCostMicros = 250_000L,
                    firedAt = "2026-04-01T12:00:00",
                )

            val result = dispatcher.dispatch("https://hooks.example.com/failing", payload)

            result.statusCode shouldBe 500
            result.responseBody shouldBe "Internal Server Error"
        }
}
