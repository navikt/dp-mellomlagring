package no.nav.dagpenger.mellomlagring.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.TestApplication
import no.nav.dagpenger.mellomlagring.TestApplication.autentisert
import no.nav.dagpenger.mellomlagring.TestApplication.withMockAuthServerAndTestApplication
import no.nav.dagpenger.mellomlagring.av.AntiVirus
import no.nav.dagpenger.mellomlagring.av.ClamAv
import no.nav.dagpenger.mellomlagring.lagring.Store
import no.nav.dagpenger.mellomlagring.lagring.VedleggMetadata
import no.nav.dagpenger.mellomlagring.lagring.VedleggService
import org.junit.jupiter.api.Test

internal class VedleggServiceTest {
    private val crypto = Config.crypto()

    private val mockAv = mockk<AntiVirus>().also {
        coEvery { it.infisert(any(), any()) } returns false
    }

    @Test
    fun `Uautorisert dersom ingen token finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            handleRequest(HttpMethod.Get, "v1/mellomlagring/1").apply {
                response.status() shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Feil dersom fil inneholder virus`() {
        val avMock = mockk<AntiVirus>().also {
            coEvery { it.infisert(any(), any()) } returns true
        }

        withMockAuthServerAndTestApplication({
            vedleggApi(VedleggService(mockk(), crypto, avMock))
        }) {
            autentisert(
                endepunkt = "v1/mellomlagring/id",
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
            }.also {
                it.response.status() shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `Autorisert dersom token finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            autentisert(endepunkt = "v1/mellomlagring/1").apply {
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Skal ikke kunne hente vedlegg som andre eier`() {
        val storeMock = mockk<Store>(relaxed = true).also {
            every { it.list(any()) } returns listOf(VedleggMetadata("fil", crypto.encrypt("eier")))
        }

        withMockAuthServerAndTestApplication({ vedleggApi(VedleggService(storeMock, crypto, mockAv)) }) {
            autentisert(
                endepunkt = "v1/mellomlagring/id",
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
            }
        }

        withMockAuthServerAndTestApplication({ vedleggApi(VedleggService(storeMock, crypto, mockAv)) }) {
            autentisert(
                endepunkt = "v1/mellomlagring?urn=urn:vedlegg:id",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }
        }
    }

    @Test
    fun `Lagring av fil`() {
        val storeMock = mockk<Store>(relaxed = true)

        withMockAuthServerAndTestApplication({ vedleggApi(VedleggService(storeMock, crypto, mockAv)) }) {
            autentisert(
                endepunkt = "v1/mellomlagring/id",
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
                    append("hubba", "file2.csv", ContentType.Text.CSV) {
                        this.append("2")
                    }
                }
                setBody("boundary", partData)
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                response.content shouldBe """[{"urn":"urn:vedlegg:id/file.csv"},{"urn":"urn:vedlegg:id/file2.csv"}]"""
                response.contentType().toString() shouldBe "application/json; charset=UTF-8"

                verify(exactly = 1) {
                    storeMock.lagre(
                        "id/file.csv",
                        "1".toByteArray(),
                        crypto.encrypt(TestApplication.defaultDummyFodselsnummer)
                    )
                }
                verify(exactly = 1) {
                    storeMock.lagre(
                        "id/file2.csv",
                        "2".toByteArray(),
                        crypto.encrypt(TestApplication.defaultDummyFodselsnummer)
                    )
                }
            }
        }
    }

    @Test
    fun `Lagring krever søknads id`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk()) }) {
            autentisert(
                endepunkt = "v1/mellomlagring/",
                httpMethod = HttpMethod.Post
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `Henting av fil som ikke eksisterer`() {
        val store = mockk<Store>().also {
            every { it.list(any()) } returns emptyList()
        }
        withMockAuthServerAndTestApplication({ vedleggApi(VedleggService(store, mockk(), mockAv)) }) {
            autentisert(
                endepunkt = "v1/mellomlagring?urn=urn:vedlegg:id",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `Hente fil`() {
        val store = mockk<Store>().also {
            every { it.hent("id") } returns "1".toByteArray()
            every { it.list(any()) } returns listOf(
                VedleggMetadata(
                    "fil",
                    crypto.encrypt(TestApplication.defaultDummyFodselsnummer)
                )
            )
        }
        withMockAuthServerAndTestApplication({ vedleggApi(VedleggService(store, crypto, mockAv)) }) {
            autentisert(
                endepunkt = "v1/mellomlagring?urn=urn:vedlegg:id",
                httpMethod = HttpMethod.Get,
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
                response.contentType() shouldBe ContentType.Application.OctetStream
                response.content shouldBe "1"
            }
        }
    }

    @Test
    fun `Hente vedleggliste`() {
        val soknadsId = "soknadsId"
        val store = mockk<Store>().also {
            every { it.list(soknadsId) } returns listOf(
                VedleggMetadata("$soknadsId/fil1", crypto.encrypt("eier1")),
                VedleggMetadata("$soknadsId/fil2", crypto.encrypt(TestApplication.defaultDummyFodselsnummer)),
            )
        }

        withMockAuthServerAndTestApplication({ vedleggApi(VedleggService(store, crypto, mockAv)) }) {
            autentisert(
                endepunkt = "v1/mellomlagring/$soknadsId",
                httpMethod = HttpMethod.Get
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
                //language=JSON
                response.content shouldBe """[{"urn":"urn:vedlegg:soknadsId/fil2"}]"""
            }
        }

        verify(exactly = 1) { store.list(soknadsId) }
    }
}
