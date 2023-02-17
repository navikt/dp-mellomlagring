package no.nav.dagpenger.mellomlagring.av

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

internal class AntiVirusTest {

    @Test
    fun `Ikke infisert fil`() {
        runBlocking {
            clamAv(
                engine = MockEngine {
                    respond(
                        content = """[{"Filename": "filnavn.pdf", "Result": "OK"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
                registry = CollectorRegistry()
            ).infisert("filnavn med masse space t.pdf", "innhold".toByteArray()) shouldBe false
        }
    }

    @Test
    fun `infisert fil`() {
        runBlocking {
            val registry = CollectorRegistry(true)
            clamAv(
                engine = MockEngine {
                    respond(
                        content = """[{"Filename": "filnavn.pdf", "Result": "FOUND"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
                registry = registry
            ).infisert("filnavn.pdf", "innhold".toByteArray()) shouldBe true

            registry.getSampleValue(
                "dp_mellomlagring_clamav_client_status_total",
                listOf("status").toTypedArray(),
                listOf("200").toTypedArray()
            ) shouldBe 1
        }
    }

    @Test
    fun `Feilhåndtering ved tom resultat liste fra clamav`() {
        runBlocking {
            shouldThrow<IllegalArgumentException> {
                clamAv(
                    engine = MockEngine {
                        respond(
                            content = """[]""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                    registry = CollectorRegistry()
                ).infisert("filnavn.pdf", "innhold".toByteArray())
            }
        }
    }
}
