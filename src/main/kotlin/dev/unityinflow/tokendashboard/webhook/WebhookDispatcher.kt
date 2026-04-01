package dev.unityinflow.tokendashboard.webhook

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

@Serializable
data class WebhookPayload(
    val alertId: String,
    val alertName: String,
    val agentId: String?,
    val period: String,
    val thresholdMicros: Long,
    val currentCostMicros: Long,
    val firedAt: String,
    val type: String = "budget_alert",
)

data class WebhookResult(
    val statusCode: Int,
    val responseBody: String?,
)

class WebhookDispatcher(
    private val httpClient: HttpClient,
    private val json: Json = Json { prettyPrint = false },
) {
    suspend fun dispatch(
        url: String,
        payload: WebhookPayload,
    ): WebhookResult {
        return try {
            val response =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(payload))
                }
            val body = response.bodyAsText()
            logger.info { "Webhook dispatched to $url — status ${response.status.value}" }
            WebhookResult(statusCode = response.status.value, responseBody = body)
        } catch (e: Exception) {
            logger.error(e) { "Webhook dispatch failed for $url" }
            WebhookResult(statusCode = -1, responseBody = e.message)
        }
    }
}
