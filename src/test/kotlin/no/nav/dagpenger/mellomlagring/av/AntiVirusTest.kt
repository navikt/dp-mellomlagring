package no.nav.dagpenger.mellomlagring.av

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

internal class AntiVirusTest {

    @Test
    fun `Ikke infisert fil`() {
        runBlocking {
            clamAv(
                MockEngine {
                    respond(
                        content = """[{"Filename": "filnavn.pdf", "Result": "OK"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            ).infisert("filnavn med masse space t.pdf", "innhold".toByteArray()) shouldBe false
        }
    }

    @Test
    fun `infisert fil`() {
        runBlocking {
            clamAv(
                MockEngine {
                    respond(
                        content = """[{"Filename": "filnavn.pdf", "Result": "FOUNOKD"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            ).infisert("filnavn.pdf", "innhold".toByteArray()) shouldBe true
        }
    }
}
