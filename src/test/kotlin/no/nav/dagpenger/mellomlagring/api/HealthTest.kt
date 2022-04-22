package no.nav.dagpenger.mellomlagring.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

internal class HealthTest {
    @Test
    fun `svarer på health tester`() {
        testApplication {
            application(Application::health)

            client.get("internal/isalive").let { httpResponse ->
                httpResponse.bodyAsText() shouldBe "alive"
                httpResponse.status shouldBe HttpStatusCode.OK
            }

            client.get("internal/isready").let { httpResponse ->
                httpResponse.bodyAsText() shouldBe "ready"
                httpResponse.status shouldBe HttpStatusCode.OK
            }
        }
    }
}
