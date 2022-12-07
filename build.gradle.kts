import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.7.22"
    id("com.diffplug.spotless") version "6.12.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
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

dependencies {
    implementation("com.google.cloud:google-cloud-storage")
    implementation("de.slub-dresden:urnlib:2.0.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.1")
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("io.github.microutils:kotlin-logging:3.0.4")
    implementation("io.ktor:ktor-client-cio:2.1.3")
    implementation("io.ktor:ktor-client-content-negotiation:2.1.3")
    implementation("io.ktor:ktor-client-core:2.1.3")
    implementation("io.ktor:ktor-client-jackson:2.1.3")
    implementation("io.ktor:ktor-serialization-jackson:2.1.3")
    implementation("io.ktor:ktor-server-auth:2.1.3")
    implementation("io.ktor:ktor-server-auth-jwt:2.1.3")
    implementation("io.ktor:ktor-server-cio:2.1.3")
    implementation("io.ktor:ktor-server-call-logging:2.1.3")
    implementation("io.ktor:ktor-server-call-id:2.1.3")
    implementation("io.ktor:ktor-server-content-negotiation:2.1.3")
    implementation("io.ktor:ktor-server-metrics-micrometer:2.1.3")
    implementation("io.ktor:ktor-server-status-pages:2.1.3")
    implementation("com.github.navikt.dp-biblioteker:image-utils:2022.11.24-14.34.528555d5f3df")
    implementation("io.micrometer:micrometer-registry-prometheus:1.10.2")
    implementation(kotlin("stdlib"))
    implementation(platform("com.google.cloud:libraries-bom:26.0.0"))
    implementation(platform(kotlin("bom")))
    implementation("com.google.crypto.tink:tink:1.7.0")
    implementation("com.google.crypto.tink:tink-gcpkms:1.7.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.1")

    runtimeOnly("ch.qos.logback:logback-core:1.4.5")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.5")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.2") {
        exclude("com.fasterxml.jackson.core")
    }

    testImplementation("no.nav.security:mock-oauth2-server:0.5.6")
    testImplementation("org.testcontainers:testcontainers:1.17.6")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.5.4")
    testImplementation("io.ktor:ktor-server-test-host:2.2.0")
    testImplementation("io.ktor:ktor-client-mock:2.1.3")
    testImplementation("io.mockk:mockk:1.13.3")
    testImplementation("org.jsmart:zerocode-tdd:1.3.31")
    testImplementation("org.jsmart:zerocode-tdd-jupiter:1.3.31")
    testImplementation("org.skyscreamer:jsonassert:1.5.1")

    // For E2E
    testImplementation("io.kubernetes:client-java:16.0.2")
    testImplementation("com.github.navikt.dp-biblioteker:oauth2-klient:2022.11.24-14.34.528555d5f3df")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")
}
