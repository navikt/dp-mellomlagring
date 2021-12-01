package no.nav.dagpenger.mellomlagring.api

import io.kotest.matchers.shouldBe
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Test

internal class HealthTest {
    @Test
    fun `svarer på health tester`() {
        withTestApplication(Application::health) {
            handleRequest(HttpMethod.Get, "internal/isalive").apply {
                response.content shouldBe "alive"
                response.status() shouldBe HttpStatusCode.OK
            }

            handleRequest(HttpMethod.Get, "internal/isready").apply {
                response.content shouldBe "ready"
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }
}
