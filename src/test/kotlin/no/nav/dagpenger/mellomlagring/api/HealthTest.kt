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
            handleRequest(HttpMethod.Get, "internal/isAlive").apply {
                response.content shouldBe "isAlive"
                response.status() shouldBe HttpStatusCode.OK
            }

            handleRequest(HttpMethod.Get, "internal/isReady").apply {
                response.content shouldBe "isReady"
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }
}
