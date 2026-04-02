package dev.unityinflow.tokendashboard.otlp

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Server
import io.grpc.ServerBuilder

private val logger = KotlinLogging.logger {}

class OtlpGrpcServer(
    private val port: Int,
    private val metricsReceiver: OtlpMetricsReceiver,
) {
    private var server: Server? = null

    fun start() {
        val grpcServer = ServerBuilder.forPort(port)
            .addService(metricsReceiver)
            .build()
            .start()

        server = grpcServer
        logger.info { "OTLP gRPC server started on port $port" }
    }

    fun stop() {
        server?.let { s ->
            logger.info { "Shutting down OTLP gRPC server" }
            s.shutdown()
            if (!s.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                s.shutdownNow()
            }
            server = null
        }
    }
}
