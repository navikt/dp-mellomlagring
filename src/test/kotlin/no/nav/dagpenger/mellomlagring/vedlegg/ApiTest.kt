package no.nav.dagpenger.mellomlagring.vedlegg

import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.features.NotFoundException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.mellomlagring.TestApplication
import no.nav.dagpenger.mellomlagring.TestApplication.autentisert
import no.nav.dagpenger.mellomlagring.TestApplication.withMockAuthServerAndTestApplication
import no.nav.dagpenger.mellomlagring.lagring.Klump
import no.nav.dagpenger.mellomlagring.lagring.KlumpInfo
import org.junit.jupiter.api.Test

internal class ApiTest {
    @Test
    fun `Uautorisert dersom ingen token finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            handleRequest(HttpMethod.Get, "v1/azuread/mellomlagring/vedlegg/1").apply {
                response.status() shouldBe HttpStatusCode.Unauthorized
            }
            handleRequest(HttpMethod.Get, "v1/obo/mellomlagring/vedlegg/1").apply {
                response.status() shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Autorisert dersom tokenx finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/1",
                token = TestApplication.tokenXToken
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Autorisert dersom azureAd finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            autentisert(
                endepunkt = "v1/azuread/mellomlagring/vedlegg/1",
                token = TestApplication.azureAd
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Liste filer for en id`() {
        val mediatorMock = mockk<Mediator>().also {
            coEvery { it.liste("id") } returns listOf(
                VedleggUrn("id/fil1"), VedleggUrn("id/fil2")
            )
            coEvery { it.liste("finnesIkke") } returns emptyList()
        }
        withMockAuthServerAndTestApplication({ vedleggApi(mediatorMock) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"
                response.content shouldBe """[{"urn":"urn:vedlegg:id/fil1"},{"urn":"urn:vedlegg:id/fil2"}]"""
            }

            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/finnesIkke",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"
                response.content shouldBe """[]"""
            }
        }
    }

    @Test
    fun `Lagring av fil`() {
        val mediator = mockk<Mediator>(relaxed = true).also {
            coEvery { it.lagre("id", "file.csv", any()) } returns VedleggUrn("id/file.csv")
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mediator) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id",
                httpMethod = HttpMethod.Post,
            ) {
                this.addHeader(
                    HttpHeaders.ContentType,
                    ContentType.MultiPart.FormData.withParameter("boundary", "boundary").toString()
                )
                val partData: List<PartData> = formData {
                    append("hubba", "file.csv", ContentType.Text.CSV) {
                        this.append("1")
                    }
                }
                setBody("boundary", partData)
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                //language=JSON
                response.content shouldBe """{"urn":"urn:vedlegg:id/file.csv"}"""
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"

                coVerify(exactly = 1) {
                    mediator.lagre(
                        soknadsId = "id",
                        filnavn = "file.csv",
                        filinnhold = "1".toByteArray()
                    )
                }
            }
        }
    }

    @Test
    fun `Hente vedlegg`() {
        val mockMediator = mockk<Mediator>().also {
            coEvery { it.hent(VedleggUrn("id/filnavn.pdf")) } returns Klump(
                innhold = "1".toByteArray(),
                klumpInfo = KlumpInfo(
                    navn = "id/filnavn.pdf",
                    metadata = mapOf()
                )
            )

            coEvery { it.hent(VedleggUrn("id/finnesIkke.pdf")) } returns null
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id/filnavn.pdf",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
                response.contentType() shouldBe ContentType.Application.OctetStream
                response.content shouldBe "1"
            }

            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id/finnesIkke.pdf",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `Slette vedlegg`() {
        val mockMediator = mockk<Mediator>().also {
            coEvery { it.slett(VedleggUrn("id/filnavn.pdf")) } returns true
            coEvery { it.slett(VedleggUrn("id/finnesIkke.pdf")) } returns false
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id/filnavn.pdf",
                httpMethod = HttpMethod.Delete,
            ).apply {
                response.status() shouldBe HttpStatusCode.NoContent
            }

            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id/finnesIkke.pdf",
                httpMethod = HttpMethod.Delete,
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun statusPages() {
        val mockMediator = mockk<Mediator>().also {
            coEvery { it.hent(VedleggUrn("id/illegalargument")) } throws IllegalArgumentException("test")
            coEvery { it.hent(VedleggUrn("id/notfound")) } throws NotFoundException("test")
            coEvery { it.hent(VedleggUrn("id/notOwner")) } throws NotOwnerException("test")
            coEvery { it.hent(VedleggUrn("id/ugyldiginnhold")) } throws UgyldigFilInnhold("test", listOf("test"))
            coEvery { it.hent(VedleggUrn("id/throwable")) } throws Throwable("test")
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id/illegalargument",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id/notfound",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
            }
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id/notOwner",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id/ugyldiginnhold",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            autentisert(
                endepunkt = "v1/obo/mellomlagring/vedlegg/id/throwable",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
            }
        }
    }
}