import Build_gradle.Versions.dpBiblioteker
import Build_gradle.Versions.ktor
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.8.10"
    id("com.diffplug.spotless") version "6.13.0"
    id("com.github.johnrengelman.shadow") version "8.1.0"
}

apply {
    plugin("com.diffplug.spotless")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

application {
    applicationName = "dp-mellomlagring"
    mainClass.set("no.nav.dagpenger.mellomlagring.AppKt")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }

    dependsOn("spotlessApply")
}

kotlin {
    jvmToolchain {
        this.languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.toString())) // "8"
    }
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<Jar>().configureEach {
    dependsOn("test")
}
spotless {
    kotlin {
        ktlint("0.43.2")
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/**/*.kt*")
        ktlint("0.43.2")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

object Versions {
    val ktor = "2.2.4"
    val dpBiblioteker = "2023.02.06-17.50.b3b2d001f61f"
}

dependencies {

    implementation("com.google.cloud:google-cloud-storage")
    implementation("de.slub-dresden:urnlib:2.0.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("io.ktor:ktor-client-cio:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-jackson:$ktor")
    implementation("io.ktor:ktor-client-logging-jvm:$ktor")
    implementation("io.ktor:ktor-serialization-jackson:$ktor")
    implementation("io.ktor:ktor-server-auth:$ktor")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor")
    implementation("io.ktor:ktor-server-cio:$ktor")
    implementation("io.ktor:ktor-server-call-logging:$ktor")
    implementation("io.ktor:ktor-server-call-id:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("com.github.navikt.dp-biblioteker:image-utils:$dpBiblioteker")
    implementation("com.github.navikt.dp-biblioteker:ktor-client-metrics:$dpBiblioteker")
    implementation("io.micrometer:micrometer-registry-prometheus:1.10.4")
    implementation(kotlin("stdlib"))
    implementation(platform("com.google.cloud:libraries-bom:26.0.0"))
    implementation(platform(kotlin("bom")))
    implementation("com.google.crypto.tink:tink:1.7.0")
    implementation("com.google.crypto.tink:tink-gcpkms:1.7.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")

    runtimeOnly("ch.qos.logback:logback-core:1.4.5")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.5")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.3") {
        exclude("com.fasterxml.jackson.core")
    }

    testImplementation("no.nav.security:mock-oauth2-server:0.5.8")
    testImplementation("org.testcontainers:testcontainers:1.17.6")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.5.5")
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.ktor:ktor-client-mock:$ktor")
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation("org.jsmart:zerocode-tdd:1.3.31")
    testImplementation("org.jsmart:zerocode-tdd-jupiter:1.3.31")
    testImplementation("org.skyscreamer:jsonassert:1.5.1")

    // For E2E
    testImplementation("io.kubernetes:client-java:17.0.1")
    testImplementation("com.github.navikt.dp-biblioteker:oauth2-klient:2022.11.24-14.34.528555d5f3df")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}
