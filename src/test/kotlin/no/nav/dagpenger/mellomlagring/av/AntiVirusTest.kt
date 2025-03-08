package no.nav.dagpenger.mellomlagring.av

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

internal class AntiVirusTest {
    @Test
    fun `Ikke infisert fil`() {
        runBlocking {
            clamAv(
                engine =
                    MockEngine {
                        respond(
                            content = """[{"Filename": "filnavn.pdf", "Result": "OK"}]""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                registry = PrometheusRegistry(),
            ).infisert("filnavn med masse space t.pdf", "innhold".toByteArray()) shouldBe false
        }
    }

    @Test
    fun `infisert fil`() {
        runBlocking {
            val registry = PrometheusRegistry()
            clamAv(
                engine =
                    MockEngine {
                        respond(
                            content = """[{"Filename": "filnavn.pdf", "Result": "FOUND"}]""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                registry = registry,
            ).infisert("filnavn.pdf", "innhold".toByteArray()) shouldBe true

            registry.scrape().also { snapshots ->
                val teller =
                    snapshots
                        .find { it.metadata.name == "dp_mellomlagring_clamav_client_status" }
                        .shouldNotBeNull() as CounterSnapshot
                teller.dataPoints
                    .first()
                    .labels
                    .get("status") shouldBe "200"
                teller.dataPoints.first().value shouldBe 1.0
            }
        }
    }

    @Test
    fun `Feilhåndtering ved tom resultat liste fra clamav`() {
        runBlocking {
            shouldThrow<IllegalArgumentException> {
                clamAv(
                    engine =
                        MockEngine {
                            respond(
                                content = """[]""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    registry = PrometheusRegistry(),
                ).infisert("filnavn.pdf", "innhold".toByteArray())
            }
        }
    }
}
