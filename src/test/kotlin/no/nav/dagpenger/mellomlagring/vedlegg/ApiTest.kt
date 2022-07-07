package no.nav.dagpenger.mellomlagring.vedlegg

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.mellomlagring.TestApplication
import no.nav.dagpenger.mellomlagring.TestApplication.autentisert
import no.nav.dagpenger.mellomlagring.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.mellomlagring.TestApplication.withMockAuthServerAndTestApplication
import no.nav.dagpenger.mellomlagring.lagring.Klump
import no.nav.dagpenger.mellomlagring.lagring.KlumpInfo
import no.nav.dagpenger.mellomlagring.test.fileAsByteArray
import org.junit.jupiter.api.Test

internal class ApiTest {
    @Test
    fun `Uautorisert dersom ingen token finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            client.get("v1/azuread/mellomlagring/vedlegg/1").status shouldBe HttpStatusCode.Unauthorized
            client.get("v1/obo/mellomlagring/vedlegg/1").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `Autentisert dersom tokenx eller azureAd finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            client.get("v1/obo/mellomlagring/vedlegg/1") { autentisert(token = TestApplication.tokenXToken) }.status shouldBe HttpStatusCode.OK
            client.get("v1/azuread/mellomlagring/vedlegg/1") {
                autentisert(token = TestApplication.azureAd)
            }.status shouldNotBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `Bad request hvis xEier header ikke er satt på kall med azuread autentisering`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            client.get("v1/azuread/mellomlagring/vedlegg/1") {
                autentisert(token = TestApplication.azureAd, xEier = defaultDummyFodselsnummer)
            }.status shouldBe HttpStatusCode.OK
            client.get("v1/azuread/mellomlagring/vedlegg/1") {
                autentisert(token = TestApplication.azureAd)
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `Liste filer for en id`() {
        val mediatorMock = mockk<Mediator>().also {
            coEvery { it.liste("id", any()) } returns listOf(
                VedleggUrn("id/fil1"), VedleggUrn("id/fil2")
            )
            coEvery { it.liste("finnesikke", defaultDummyFodselsnummer) } returns emptyList()
        }
        withMockAuthServerAndTestApplication({ vedleggApi(mediatorMock) }) {
            client.get("v1/obo/mellomlagring/vedlegg/id") { autentisert() }.let { response ->
                response.status shouldBe HttpStatusCode.OK
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"
                response.bodyAsText() shouldBe """[{"urn":"urn:vedlegg:id/fil1"},{"urn":"urn:vedlegg:id/fil2"}]"""
            }

            client.get("v1/obo/mellomlagring/vedlegg/finnesikke") { autentisert() }.let { response ->
                response.status shouldBe HttpStatusCode.OK
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"
                response.bodyAsText() shouldBe """[]"""
            }
        }
    }

    sealed class TestFixture {

        data class TokenX() : TestFixture
        data class AzureAd() : TestFixture
    }

    @Test
    fun `Lagring av fil`() {
        val mediator = mockk<Mediator>(relaxed = true).also {
            coEvery { it.lagre("id", "file.csv", any(), any()) } returns VedleggUrn("id/file.csv")
            coEvery { it.lagre("id", "file2.csv", any(), any()) } returns VedleggUrn("id/file2.csv")
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mediator) }) {
            listOf(
                Pair("obo", TestApplication.tokenXToken),
                Pair("azuread", TestApplication.azureAd)
            ).forEach { fixture ->

                client.post("v1/${fixture.first}/mellomlagring/vedlegg/id") {
                    autentisert(token = fixture.second)
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("hubba", "file.csv", ContentType.Text.CSV) {
                                    this.append("1")
                                }
                                append("hubba", "file2.csv", ContentType.Text.CSV) {
                                    this.append("2")
                                }
                            },
                            "boundary",
                            ContentType.MultiPart.FormData.withParameter("boundary", "boundary")
                        )
                    )
                }.let { response ->
                    response.status shouldBe HttpStatusCode.Created
                    //language=JSON
                    response.bodyAsText() shouldBe """[{"filnavn":"file.csv","urn":"urn:vedlegg:id/file.csv"},{"filnavn":"file2.csv","urn":"urn:vedlegg:id/file2.csv"}]"""
                    response.contentType().toString() shouldBe "application/json; charset=UTF-8"

                    coVerify(exactly = 1) {
                        mediator.lagre(
                            soknadsId = "id",
                            filnavn = "file.csv",
                            filinnhold = "1".toByteArray(),
                            defaultDummyFodselsnummer
                        )
                    }

                    coVerify(exactly = 1) {
                        mediator.lagre(
                            soknadsId = "id",
                            filnavn = "file2.csv",
                            filinnhold = "2".toByteArray(),
                            defaultDummyFodselsnummer
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `bundling av filer`() {
        val mediator = mockk<Mediator>(relaxed = true).also {
            coEvery { it.lagre("id", "bundle.pdf", any(), any()) } returns VedleggUrn("id/bundle.pdf")
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mediator) }) {
            client.post("v1/obo/mellomlagring/bundle/id") {
                lagDataBundle(
                    listOf(
                        Fil(type = "image/jpeg", path = "/fisk1.jpg", navn = "fisk1.jpg"),
                        Fil(type = "image/jpeg", path = "/fisk2.jpg", navn = "fisk2.jpg")
                    )
                )
            }.let { response ->
                response.status shouldBe HttpStatusCode.Created
                //language=JSON
                response.bodyAsText() shouldBe """{"filnavn":"bundle.pdf","urn":"urn:vedlegg:id/bundle.pdf"}"""
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"
            }

            client.post("v1/obo/mellomlagring/bundle/id") {
                lagDataBundle(
                    listOf(
                        Fil(type = "image/jpeg", path = "/fisk1.jpg", navn = "fisk1.jpg")
                    )
                )
            }.let { response ->
                response.status shouldBe HttpStatusCode.Created
                //language=JSON
                response.bodyAsText() shouldBe """{"filnavn":"bundle.pdf","urn":"urn:vedlegg:id/bundle.pdf"}"""
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"
            }

            client.post("v1/obo/mellomlagring/bundle/id") {
                lagDataBundle(
                    listOf(
                        Fil(type = "application/pdf", path = "/Arbeidsforhold.pdf", navn = "Arbeidsforhol")
                    )
                )
            }.let { response ->
                response.status shouldBe HttpStatusCode.Created
                //language=JSON
                response.bodyAsText() shouldBe """{"filnavn":"bundle.pdf","urn":"urn:vedlegg:id/bundle.pdf"}"""
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"
            }

            client.post("v1/obo/mellomlagring/bundle/id") {
                lagDataBundle(listOf())
            }.let { response ->
                response.status shouldBe HttpStatusCode.BadRequest
            }

            client.post("v1/obo/mellomlagring/bundle/id") {
                lagDataBundle(
                    listOf(
                        Fil(type = "application/pdf", path = "/Arbeidsforhold.pdf", navn = "Arbeidsforhold"),
                        Fil(type = "text/plain", path = "/test.txt", navn = "test"),
                        Fil(type = "image/jpeg", path = "/fisk1.jpg", navn = "fisk1.jpg")
                    )
                )
            }.let { response ->
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `Hente vedlegg`() {
        val mockMediator = mockk<Mediator>().also {
            coEvery { it.hent(VedleggUrn("id/filnavn.pdf"), defaultDummyFodselsnummer) } returns Klump(
                innhold = "1".toByteArray(),
                klumpInfo = KlumpInfo(
                    navn = "id/filnavn.pdf",
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
            client.get("v1/obo/mellomlagring/vedlegg/id/filnavn.pdf") { autentisert() }.let { response ->
                response.status shouldBe HttpStatusCode.OK
                response.contentType() shouldBe ContentType.Application.OctetStream
                response.bodyAsText() shouldBe "1"
            }

            client.get("v1/obo/mellomlagring/vedlegg/id/finnesIkke.pdf") { autentisert() }.status shouldBe HttpStatusCode.NotFound
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
            client.delete("v1/obo/mellomlagring/vedlegg/id/filnavn.pdf") { autentisert() }.status shouldBe HttpStatusCode.NoContent
            client.delete("v1/obo/mellomlagring/vedlegg/id/finnesIkke.pdf") { autentisert() }.status shouldBe HttpStatusCode.NotFound
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

    private fun HttpRequestBuilder.lagDataBundle(filer: List<Fil>) {
        autentisert()
        setBody(
            MultiPartFormDataContent(
                formData {
                    filer.forEach {
                        append(
                            "image", it.path.fileAsByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, it.type)
                                append(HttpHeaders.ContentDisposition, "filename=\"${it.navn}\"")
                            }
                        )
                    }
                    append("bundleFilnavn", "bundle.pdf")
                }
            )
        )
    }

    private data class Fil(val type: String, val path: String, val navn: String)
}
