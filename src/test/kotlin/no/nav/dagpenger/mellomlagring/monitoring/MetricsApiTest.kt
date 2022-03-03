package no.nav.dagpenger.mellomlagring.monitoring

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Test

internal class MetricsApiTest {
    @Test
    fun `metrikker er tilgjengelig på riktig endepunkt`() {
        withTestApplication(Application::metrics) {
            handleRequest(HttpMethod.Get, "internal/metrics").apply {
                response.status() shouldBe HttpStatusCode.OK
                response.content shouldContain ("ktor_http_server_requests_active")
            }
        }
    }
}
