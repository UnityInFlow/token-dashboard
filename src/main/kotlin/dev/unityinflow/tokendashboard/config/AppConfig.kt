package dev.unityinflow.tokendashboard.config

data class AppConfig(
    val host: String,
    val port: Int,
    val dbPath: String,
    val otlpGrpcPort: Int,
) {
    companion object {
        fun load(): AppConfig =
            AppConfig(
                host = System.getenv("TD_HOST") ?: "0.0.0.0",
                port = System.getenv("TD_PORT")?.toIntOrNull() ?: 8080,
                dbPath = System.getenv("TD_DB_PATH") ?: "token-dashboard.db",
                otlpGrpcPort = System.getenv("TD_OTLP_PORT")?.toIntOrNull() ?: 4317,
            )
    }
}
