package dev.unityinflow.tokendashboard.otlp

import dev.unityinflow.tokendashboard.ingestion.IngestionService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.stub.StreamObserver
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc

private val logger = KotlinLogging.logger {}

class OtlpMetricsReceiver(
    private val ingestionService: IngestionService,
) : MetricsServiceGrpc.MetricsServiceImplBase() {
    override fun export(
        request: ExportMetricsServiceRequest,
        responseObserver: StreamObserver<ExportMetricsServiceResponse>,
    ) {
        try {
            val records = OtlpMetricMapper.mapToIngestRecords(request)
            if (records.isNotEmpty()) {
                val result = ingestionService.ingest(records)
                logger.info { "OTLP ingestion: accepted=${result.accepted}, errors=${result.errors.size}" }
            }
            responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance())
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to process OTLP metrics export" }
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Failed to process metrics: ${e.message}")
                    .asRuntimeException(),
            )
        }
    }
}
