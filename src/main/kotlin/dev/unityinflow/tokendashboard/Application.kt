package dev.unityinflow.tokendashboard

import dev.unityinflow.tokendashboard.api.agentRoutes
import dev.unityinflow.tokendashboard.api.alertRoutes
import dev.unityinflow.tokendashboard.api.costRoutes
import dev.unityinflow.tokendashboard.api.healthRoutes
import dev.unityinflow.tokendashboard.api.ingestRoutes
import dev.unityinflow.tokendashboard.api.sessionRoutes
import dev.unityinflow.tokendashboard.config.AppConfig
import dev.unityinflow.tokendashboard.db.DatabaseFactory
import dev.unityinflow.tokendashboard.ingestion.IngestionService
import dev.unityinflow.tokendashboard.otlp.OtlpGrpcServer
import dev.unityinflow.tokendashboard.otlp.OtlpMetricsReceiver
import dev.unityinflow.tokendashboard.service.AlertEvaluationService
import dev.unityinflow.tokendashboard.service.AnomalyDetector
import dev.unityinflow.tokendashboard.web.htmxFragments
import dev.unityinflow.tokendashboard.web.pageRoutes
import dev.unityinflow.tokendashboard.webhook.WebhookDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database

private val logger = KotlinLogging.logger {}

fun main() {
    val config = AppConfig.load()
    logger.info { "Starting token-dashboard on ${config.host}:${config.port}" }

    embeddedServer(Netty, port = config.port, host = config.host) {
        configureApp(config)
    }.start(wait = true)
}

fun Application.configureApp(config: AppConfig) {
    val db = DatabaseFactory.init(config.dbPath)

    val httpClient = HttpClient(CIO)
    val webhookDispatcher = WebhookDispatcher(httpClient)
    val anomalyDetector = AnomalyDetector()
    val alertService = AlertEvaluationService(db, webhookDispatcher, anomalyDetector)

    val alertScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    alertService.start(alertScope)

    val ingestionService = IngestionService(db)
    val metricsReceiver = OtlpMetricsReceiver(ingestionService)
    val otlpServer = OtlpGrpcServer(config.otlpGrpcPort, metricsReceiver)
    otlpServer.start()

    monitor.subscribe(ApplicationStopped) {
        otlpServer.stop()
        alertService.stop()
        httpClient.close()
    }

    configureAppWithDb(db, alertService)
}

fun Application.configureAppWithDb(
    db: Database,
    alertEvaluationService: AlertEvaluationService? = null,
) {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            },
        )
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(CallLogging)

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respondText(
                text = """{"error": "${cause.message}"}""",
                status = HttpStatusCode.BadRequest,
            )
        }
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception" }
            call.respondText(
                text = """{"error": "Internal server error"}""",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }

    routing {
        healthRoutes(db)
        ingestRoutes(db)
        sessionRoutes(db)
        agentRoutes(db)
        costRoutes(db)
        alertRoutes(db)
        pageRoutes(db)
        htmxFragments(db)
    }
}
