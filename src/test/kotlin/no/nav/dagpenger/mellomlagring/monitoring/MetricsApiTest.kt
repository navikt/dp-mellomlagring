package no.nav.dagpenger.mellomlagring.monitoring

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

internal class MetricsApiTest {
    @Test
    fun `metrikker er tilgjengelig på riktig endepunkt`() {
        testApplication {
            application(Application::metrics)

            client.get("internal/metrics").let { response ->
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain ("ktor_http_server_requests_active")
            }
        }
    }
}
