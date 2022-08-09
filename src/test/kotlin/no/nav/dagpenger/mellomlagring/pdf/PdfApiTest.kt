package no.nav.dagpenger.mellomlagring.pdf

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.mellomlagring.TestApplication.autentisert
import no.nav.dagpenger.mellomlagring.TestApplication.azureAd
import no.nav.dagpenger.mellomlagring.TestApplication.tokenXToken
import no.nav.dagpenger.mellomlagring.TestApplication.withMockAuthServerAndTestApplication
import no.nav.dagpenger.mellomlagring.lagring.KlumpInfo
import org.junit.jupiter.api.Test

internal class PdfApiTest {
    companion object {
        const val bundlePath = "v1/mellomlagring/pdf/bundle"
    }

    @Test
    fun `Uautorisert dersom ikke azuread token finnes`() {
        withMockAuthServerAndTestApplication({ pdfApi(mockk(relaxed = true)) }) {
            client.post(bundlePath).status shouldBe HttpStatusCode.Unauthorized
            client.post(bundlePath) { autentisert(token = tokenXToken) }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `Ugyldig request dersom ikke eier finnes`() {
        withMockAuthServerAndTestApplication({ pdfApi(mockk(relaxed = true)) }) {
            client.post(bundlePath) {
                autentisert(token = azureAd, xEier = null)
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{}""")
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `Autoriset dersom azuread token og eier er i request`() {
        withMockAuthServerAndTestApplication({ pdfApi(mockk(relaxed = true)) }) {
            client.post(bundlePath) {
                autentisert(
                    token = azureAd,
                    xEier = "123"
                )
            }.status shouldNotBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `happy days`() {
        withMockAuthServerAndTestApplication({
            pdfApi(
                mockk<BundleMediator>(relaxed = true).also {
                    coEvery { it.bundle(any(), "123") } returns KlumpInfo("bundle.pdf", emptyMap())
                }
            )
        }) {
            client.post(bundlePath) {
                autentisert(token = azureAd, xEier = "123")
                header(HttpHeaders.ContentType, "application/json")
                setBody(
                    """
                      { 
                        "bundleNavn": "bundle.pdf",
                        "soknadId": "id1000",
                        "filer": [
                          {"urn": "urn:vedlegg:id1000/fil1.jpg"},
                          {"urn": "urn:vedlegg:id1000/fil2.jpg"}
                        ]   
                      }
                    """
                )
            }.let { response ->
                response.status shouldBe HttpStatusCode.Created
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"
                //language=JSON
                response.bodyAsText() shouldBe """{"filnavn":"bundle.pdf","urn":{"urn":"urn:vedlegg:bundle.pdf"}}"""
            }
        }
    }
}
