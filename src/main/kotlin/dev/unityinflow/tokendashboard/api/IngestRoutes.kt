package dev.unityinflow.tokendashboard.api

import dev.unityinflow.tokendashboard.ingestion.IngestRequest
import dev.unityinflow.tokendashboard.ingestion.IngestionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.Database

fun Route.ingestRoutes(db: Database) {
    val ingestionService = IngestionService(db)

    route("/api/v1") {
        post("/ingest") {
            val request = call.receive<IngestRequest>()

            if (request.records.isEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "records list must not be empty"),
                )
                return@post
            }

            val response = ingestionService.ingest(request.records)
            call.respond(HttpStatusCode.Accepted, response)
        }
    }
}
