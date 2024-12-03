import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    alias(libs.plugins.kotlin)
    alias(libs.plugins.spotless)
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

application {
    applicationName = "dp-mellomlagring"
    mainClass.set("no.nav.dagpenger.mellomlagring.AppKt")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
    }
    dependsOn("spotlessApply")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        ktlint()
    }

    kotlinGradle {
        ktlint()
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
    implementation(kotlin("stdlib"))
    implementation(platform(kotlin("bom")))

    implementation(platform("com.google.cloud:libraries-bom:26.25.0"))
    implementation("com.google.cloud:google-cloud-storage")

    implementation(project(":openapi"))

    implementation(libs.jackson.core)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation("no.nav.dagpenger:image-utils:2024.10.24-13.47.d2fb89767416")
    implementation(libs.dp.biblioteker.ktor.klient.metrics)

    implementation("io.micrometer:micrometer-registry-prometheus:1.13.6")
    implementation(libs.jackson.datatype.jsr310)
    implementation("io.ktor:ktor-server-swagger:${libs.versions.ktor.get()}")

    implementation("com.google.crypto.tink:tink:1.15.0")
    implementation("com.google.crypto.tink:tink-gcpkms:1.10.0")
    implementation("de.slub-dresden:urnlib:2.0.1")

    runtimeOnly(libs.logback.core)
    runtimeOnly(libs.logback.classic)
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.4") {
        exclude("com.fasterxml.jackson.core")
    }

    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.testcontainer)

    val junitVersion = libs.versions.junit.get()
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation(libs.kotest.assertions.core)

    val ktorVersion = libs.versions.ktor.get()
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")

    testImplementation(libs.mockk)

    testImplementation("org.jsmart:zerocode-tdd:1.3.44")
    testImplementation("org.jsmart:zerocode-tdd-jupiter:1.3.44")

    testImplementation("org.skyscreamer:jsonassert:1.5.3")

    // For E2E
    testImplementation("io.kubernetes:client-java:21.0.2")
    testImplementation(libs.dp.biblioteker.oauth2.klient)
}
