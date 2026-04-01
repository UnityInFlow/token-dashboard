plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

group = "dev.unityinflow"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.0.3"
val exposedVersion = "0.57.0"
val otelVersion = "1.45.0"
val grpcVersion = "1.69.0"
val kotlinxCoroutinesVersion = "1.10.1"
val logbackVersion = "1.5.15"
val kotestVersion = "5.9.1"
val mockkVersion = "1.13.14"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")

    // Database: Exposed + SQLite
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")

    // OpenTelemetry
    implementation("io.opentelemetry:opentelemetry-api:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-sdk:$otelVersion")
    implementation("io.opentelemetry.proto:opentelemetry-proto:1.4.0-alpha")

    // gRPC for OTLP receiver
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // CLI
    implementation("com.github.ajalt.clikt:clikt:5.0.2")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoroutinesVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("token-dashboard")
    archiveClassifier.set("all")
    manifest {
        attributes("Main-Class" to "dev.unityinflow.tokendashboard.ApplicationKt")
    }
}

kotlin {
    jvmToolchain(21)
}
