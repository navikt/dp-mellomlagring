package no.nav.dagpenger.mellomlagring.vedlegg

import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.mellomlagring.TestApplication
import no.nav.dagpenger.mellomlagring.TestApplication.autentisert
import no.nav.dagpenger.mellomlagring.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.mellomlagring.TestApplication.withMockAuthServerAndTestApplication
import no.nav.dagpenger.mellomlagring.lagring.Klump
import no.nav.dagpenger.mellomlagring.lagring.KlumpInfo
import org.junit.jupiter.api.Test

internal class ApiTest {

    @Test
    fun `Uautorisert dersom ingen token finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            listOf(TestFixture.TokenX(), TestFixture.AzureAd()).forEach { fixture ->
                client.get("${fixture.path}/vedlegg/1").status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Autentisert dersom token finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            listOf(TestFixture.TokenX(), TestFixture.AzureAd()).forEach { fixture ->
                client.get("${fixture.path}/vedlegg/1") { autentisert(fixture) }.status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Bad request hvis xEier header ikke er satt på kall med azuread autentisering`() {
        TestFixture.AzureAd().let { fixture ->
            withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
                client.get("${fixture.path}/vedlegg/1") {
                    autentisert(
                        token = TestApplication.azureAd,
                        xEier = defaultDummyFodselsnummer
                    )
                }.status shouldBe HttpStatusCode.OK

                client.get("${fixture.path}/vedlegg/1") { autentisert(token = TestApplication.azureAd) }.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `Liste filer for en id`() {
        val mediator = mockk<Mediator>().also {
            coEvery { it.liste("id", any()) } returns listOf(
                KlumpInfo("id/fil1", mapOf("filnavn" to "fil1")), KlumpInfo("id/fil2", mapOf("filnavn" to "a b c"))
            )
            coEvery { it.liste("finnesikke", defaultDummyFodselsnummer) } returns emptyList()
        }
        withMockAuthServerAndTestApplication({ vedleggApi(mediator) }) {
            listOf(TestFixture.TokenX(), TestFixture.AzureAd()).forEach { fixture ->
                client.get("${fixture.path}/vedlegg/id") { autentisert(fixture) }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.contentType().toString() shouldBe "application/json; charset=UTF-8"
                    //language=JSON
                    response.bodyAsText() shouldBe """[{"filnavn":"fil1","urn":"urn:vedlegg:id/fil1"},{"filnavn":"a b c","urn":"urn:vedlegg:id/fil2"}]"""
                }

                client.get("${fixture.path}/vedlegg/finnesikke") { autentisert(fixture) }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.contentType().toString() shouldBe "application/json; charset=UTF-8"
                    response.bodyAsText() shouldBe """[]"""
                }
            }
        }
    }

    @Test
    fun `Lagring av fil`() {
        val mediator = mockk<Mediator>().also {
            coEvery { it.lagre("id", "file.csv", any(), defaultDummyFodselsnummer) } returns KlumpInfo("id/file.csv")
            coEvery { it.lagre("id", "file2.csv", any(), defaultDummyFodselsnummer) } returns KlumpInfo("id/file2.csv")
            coEvery { it.lagre("id", "fil med space", any(), defaultDummyFodselsnummer) } returns KlumpInfo(
                "id/uuid",
                mapOf("filnavn" to "fil med space")
            )
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mediator) }) {
            listOf(TestFixture.TokenX(), TestFixture.AzureAd()).forEach { fixture ->
                client.post("${fixture.path}/vedlegg/id") {
                    autentisert(fixture)
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("hubba", "file.csv", ContentType.Text.CSV) {
                                    this.append("1")
                                }
                                append("hubba", "file2.csv", ContentType.Text.CSV) {
                                    this.append("2")
                                }
                                append("hubba", "fil med space", ContentType.Text.CSV) {
                                    this.append("3")
                                }
                            },
                            "boundary",
                            ContentType.MultiPart.FormData.withParameter("boundary", "boundary")
                        )
                    )
                }.let { response ->
                    response.status shouldBe HttpStatusCode.Created
                    //language=JSON
                    response.bodyAsText() shouldBe """[{"filnavn":"id/file.csv","urn":"urn:vedlegg:id/file.csv"},{"filnavn":"id/file2.csv","urn":"urn:vedlegg:id/file2.csv"},{"filnavn":"fil med space","urn":"urn:vedlegg:id/uuid"}]"""
                    response.contentType().toString() shouldBe "application/json; charset=UTF-8"
                }
            }
        }
    }

    @Test
    fun `Hente vedlegg`() {
        val mockMediator = mockk<Mediator>().also {
            coEvery { it.hent(VedleggUrn("id/filnavn.pdf"), defaultDummyFodselsnummer) } returns Klump(
                innhold = "1".toByteArray(),
                klumpInfo = KlumpInfo(
                    objektNavn = "id/filnavn.pdf",
                    metadata = mapOf()
                )
            )
            coEvery {
                it.hent(
                    VedleggUrn("id/finnesIkke.pdf"),
                    defaultDummyFodselsnummer
                )
            } throws NotFoundException("hjk")
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            listOf(TestFixture.TokenX(), TestFixture.AzureAd()).forEach { fixture ->
                client.get("${fixture.path}/vedlegg/id/filnavn.pdf") { autentisert(fixture) }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.contentType() shouldBe ContentType.Application.OctetStream
                    response.bodyAsText() shouldBe "1"
                }

                client.get("${fixture.path}/vedlegg/id/finnesIkke.pdf") { autentisert(fixture) }.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `Slette vedlegg`() {
        val mockMediator = mockk<Mediator>().also {
            coEvery { it.slett(VedleggUrn("id/filnavn.pdf"), defaultDummyFodselsnummer) } returns true
            coEvery {
                it.slett(
                    VedleggUrn("id/finnesIkke.pdf"),
                    defaultDummyFodselsnummer
                )
            } throws NotFoundException("hjkhk")
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            listOf(TestFixture.TokenX(), TestFixture.AzureAd()).forEach { fixture ->
                client.delete("${fixture.path}/vedlegg/id/filnavn.pdf") { autentisert(fixture) }.status shouldBe HttpStatusCode.NoContent
                client.delete("${fixture.path}/vedlegg/id/finnesIkke.pdf") { autentisert(fixture) }.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun statusPages() {
        val mockMediator = mockk<Mediator>().also {
            coEvery { it.hent(VedleggUrn("id/illegalargument"), any()) } throws IllegalArgumentException("test")
            coEvery { it.hent(VedleggUrn("id/notOwner"), any()) } throws NotOwnerException("test")
            coEvery { it.hent(VedleggUrn("id/ugyldiginnhold"), any()) } throws UgyldigFilInnhold("test", listOf("test"))
            coEvery { it.hent(VedleggUrn("id/throwable"), any()) } throws Throwable("test")
            coEvery { it.hent(VedleggUrn("id/notfound"), any()) } throws NotFoundException("test")
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            client.get("v1/obo/mellomlagring/vedlegg/id/illegalargument") { autentisert() }.status shouldBe HttpStatusCode.BadRequest
            client.get("v1/obo/mellomlagring/vedlegg/id/notOwner") { autentisert() }.status shouldBe HttpStatusCode.Forbidden
            client.get("v1/obo/mellomlagring/vedlegg/id/ugyldiginnhold") { autentisert() }.status shouldBe HttpStatusCode.BadRequest
            client.get("v1/obo/mellomlagring/vedlegg/id/throwable") { autentisert() }.status shouldBe HttpStatusCode.InternalServerError
            client.get("v1/obo/mellomlagring/vedlegg/id/notfound") { autentisert() }.status shouldBe HttpStatusCode.NotFound
        }
    }

    private sealed class TestFixture(val path: String, val token: String) {
        class TokenX() : TestFixture("v1/obo/mellomlagring/", TestApplication.tokenXToken)

        data class AzureAd(val eier: String = defaultDummyFodselsnummer) :
            TestFixture("v1/azuread/mellomlagring/", TestApplication.azureAd)
    }

    private fun HttpRequestBuilder.autentisert(fixture: TestFixture) {
        this.header(HttpHeaders.Authorization, "Bearer ${fixture.token}")
        when (fixture) {
            is TestFixture.AzureAd -> this.header("X-Eier", fixture.eier)
            else -> {}
        }
    }

    private data class Fil(val type: String, val path: String, val navn: String)
}
