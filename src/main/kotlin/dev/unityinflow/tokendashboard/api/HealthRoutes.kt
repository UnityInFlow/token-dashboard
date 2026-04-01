package dev.unityinflow.tokendashboard.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
)

fun Route.healthRoutes(db: Database) {
    route("/api/v1") {
        get("/health") {
            val dbStatus =
                try {
                    transaction(db) {
                        exec("SELECT 1") {
                            it.next()
                            true
                        }
                    }
                    "connected"
                } catch (_: Exception) {
                    "disconnected"
                }

            val status = if (dbStatus == "connected") "healthy" else "unhealthy"
            val httpStatus = if (dbStatus == "connected") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable

            call.respond(httpStatus, HealthResponse(status = status, database = dbStatus))
        }
    }
}
