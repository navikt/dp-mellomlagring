import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(Spotless.spotless) version Spotless.version
    id(Shadow.shadow) version Shadow.version
}

apply {
    plugin(Spotless.spotless)
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
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.toString())) // "8"
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
        ktlint(Ktlint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/**/*.kt*")
        ktlint(Ktlint.version)
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
    implementation(Jackson.kotlin)
    implementation(Konfig.konfig)
    implementation(Kotlin.Logging.kotlinLogging)
    implementation(Ktor2.Client.library("cio"))
    implementation(Ktor2.Client.library("content-negotiation"))
    implementation(Ktor2.Client.library("core"))
    implementation(Ktor2.Client.library("jackson"))
    implementation("io.ktor:ktor-serialization-jackson:${Ktor2.version}")
    implementation(Ktor2.Server.library("auth"))
    implementation(Ktor2.Server.library("auth-jwt"))
    implementation(Ktor2.Server.library("cio"))
    implementation(Ktor2.Server.library("call-logging"))
    implementation(Ktor2.Server.library("content-negotiation"))
    implementation(Ktor2.Server.library("metrics-micrometer"))
    implementation(Ktor2.Server.library("status-pages"))
    implementation("com.github.navikt.dp-biblioteker:image-utils:2022.06.27-11.37.720934709949")
    implementation(Micrometer.prometheusRegistry)
    implementation(kotlin("stdlib"))
    implementation(platform("com.google.cloud:libraries-bom:24.3.0"))
    implementation(platform(kotlin("bom")))
    implementation("com.google.crypto.tink:tink:1.6.1")

    runtimeOnly("ch.qos.logback:logback-classic:1.3.0-alpha11")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.0.1") {
        exclude("com.fasterxml.jackson.core")
    }

    testImplementation("no.nav.security:mock-oauth2-server:0.4.5")
    testImplementation("org.testcontainers:testcontainers:${TestContainers.version}")
    testImplementation(Junit5.api)
    testImplementation(KoTest.assertions)
    testImplementation(Ktor2.Server.library("test-host"))
    testImplementation(Ktor2.Client.library("mock"))
    testImplementation(Mockk.mockk)

    testRuntimeOnly(Junit5.engine)
}
