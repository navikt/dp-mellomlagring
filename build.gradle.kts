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

dependencies {
    implementation("com.google.cloud:google-cloud-storage")
    implementation("de.slub-dresden:urnlib:2.0.1")
    implementation("io.micrometer:micrometer-registry-prometheus:1.8.3")
    implementation("no.nav.security:token-validation-ktor:1.3.10")
    implementation(Konfig.konfig)
    implementation(Kotlin.Logging.kotlinLogging)
    implementation(Ktor.library("auth-jwt"))
    implementation(Ktor.library("client-cio-jvm"))
    implementation(Ktor.library("client-core"))
    implementation(Ktor.library("client-jackson"))
    implementation(Ktor.library("jackson"))
    implementation(Ktor.library("metrics-micrometer"))
    implementation(Ktor.library("server-cio"))
    implementation(Ktor.server)
    implementation(kotlin("stdlib"))
    implementation(platform("com.google.cloud:libraries-bom:24.3.0"))
    implementation(platform(kotlin("bom")))

    runtimeOnly("ch.qos.logback:logback-classic:1.3.0-alpha10")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.0") {
        exclude("com.fasterxml.jackson.core")
    }

    testImplementation("no.nav.security:mock-oauth2-server:0.4.3")
    testImplementation("org.testcontainers:testcontainers:${TestContainers.version}")
    testImplementation(Junit5.api)
    testImplementation(KoTest.assertions)
    testImplementation(Ktor.library("server-test-host"))
    testImplementation(Ktor.library("client-mock"))
    testImplementation(Mockk.mockk)

    testRuntimeOnly(Junit5.engine)
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

tasks.withType<KotlinCompile>().configureEach {
    dependsOn("spotlessApply")
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
