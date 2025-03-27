plugins {
    java
    `java-library`
}

group = "worker.traces"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

object Versions {
    const val vertx = "4.5.12"
    const val openTelemetry = "1.32.0"
    const val slf4j = "2.0.9"
    const val log4j = "2.22.1"
}


dependencies {
    implementation("org.slf4j:slf4j-api:${Versions.slf4j}")

    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:${Versions.log4j}")
    implementation("org.apache.logging.log4j:log4j-core:${Versions.log4j}")

    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:${Versions.openTelemetry}")

    implementation("io.vertx:vertx-core:${Versions.vertx}")
    implementation("io.vertx:vertx-opentelemetry:${Versions.vertx}")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    register<JavaExec>("startWorkerTraceMain") {
        group = "launch"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("vertx.worker.traces.WorkerTraceMain")
        workingDir = rootProject.projectDir
        jvmArgs = listOf(
            "-javaagent:src/main/resources/opentelemetry-javaagent.jar",
            "-Dotel.javaagent.configuration-file=src/main/resources/config/otel-config.properties",
            "-Dotel.service.name=main"
        )
    }
}

tasks.test {
    useJUnitPlatform()
}