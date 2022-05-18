package no.nav.dagpenger.mellomlagring.vedlegg

import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
            client.get("v1/azuread/mellomlagring/vedlegg/1").status shouldBe HttpStatusCode.Unauthorized
            client.get("v1/obo/mellomlagring/vedlegg/1").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `Autorisert dersom tokenx finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            client.get("v1/obo/mellomlagring/vedlegg/1") { autentisert(token = TestApplication.tokenXToken) }.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `Autorisert dersom azureAd finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            client.get("v1/azuread/mellomlagring/vedlegg/1") { autentisert(token = TestApplication.azureAd) }.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `Liste filer for en id`() {
        val mediatorMock = mockk<Mediator>().also {
            coEvery { it.liste("id") } returns listOf(
                VedleggUrn("id/fil1"), VedleggUrn("id/fil2")
            )
            coEvery { it.liste("finnesikke") } returns emptyList()
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

    @Test
    fun `Lagring av fil`() {
        val mediator = mockk<Mediator>(relaxed = true).also {
            coEvery { it.lagre("id", "file.csv", any()) } returns VedleggUrn("id/file.csv")
            coEvery { it.lagre("id", "file2.csv", any()) } returns VedleggUrn("id/file2.csv")
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mediator) }) {
            client.post("v1/obo/mellomlagring/vedlegg/id") {
                autentisert()
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
                        filinnhold = "1".toByteArray()
                    )
                }

                coVerify(exactly = 1) {
                    mediator.lagre(
                        soknadsId = "id",
                        filnavn = "file2.csv",
                        filinnhold = "2".toByteArray()
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
            coEvery { it.slett(VedleggUrn("id/filnavn.pdf")) } returns true
            coEvery { it.slett(VedleggUrn("id/finnesIkke.pdf")) } returns false
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            client.delete("v1/obo/mellomlagring/vedlegg/id/filnavn.pdf") { autentisert() }.status shouldBe HttpStatusCode.NoContent
            client.delete("v1/obo/mellomlagring/vedlegg/id/finnesIkke.pdf") { autentisert() }.status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun statusPages() {
        val mockMediator = mockk<Mediator>().also {
            coEvery { it.hent(VedleggUrn("id/illegalargument")) } throws IllegalArgumentException("test")
            coEvery { it.hent(VedleggUrn("id/notOwner")) } throws NotOwnerException("test")
            coEvery { it.hent(VedleggUrn("id/ugyldiginnhold")) } throws UgyldigFilInnhold("test", listOf("test"))
            coEvery { it.hent(VedleggUrn("id/throwable")) } throws Throwable("test")
        }

        withMockAuthServerAndTestApplication({ vedleggApi(mockMediator) }) {
            client.get("v1/obo/mellomlagring/vedlegg/id/illegalargument") { autentisert() }.status shouldBe HttpStatusCode.BadRequest
            client.get("v1/obo/mellomlagring/vedlegg/id/notOwner") { autentisert() }.status shouldBe HttpStatusCode.Forbidden
            client.get("v1/obo/mellomlagring/vedlegg/id/ugyldiginnhold") { autentisert() }.status shouldBe HttpStatusCode.BadRequest
            client.get("v1/obo/mellomlagring/vedlegg/id/throwable") { autentisert() }.status shouldBe HttpStatusCode.InternalServerError
        }
    }
}
