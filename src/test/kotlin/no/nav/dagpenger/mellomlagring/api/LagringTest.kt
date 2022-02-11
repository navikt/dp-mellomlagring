package no.nav.dagpenger.mellomlagring.api

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.mellomlagring.TestApplication.autentisert
import no.nav.dagpenger.mellomlagring.TestApplication.withMockAuthServerAndTestApplication
import no.nav.dagpenger.mellomlagring.lagring.VedleggMetadata
import no.nav.dagpenger.mellomlagring.lagring.VedleggService
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.Test

internal class LagringTest {

    private val mockOAuth2Server: MockOAuth2Server by lazy {
        MockOAuth2Server().also { server ->
            server.start()
        }
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
    fun `Autorisert dersom token finnes`() {
        withMockAuthServerAndTestApplication({ vedleggApi(mockk(relaxed = true)) }) {
            autentisert(endepunkt = "v1/mellomlagring/1").apply {
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Lagring av fil`() {
        val soknadsid = mutableListOf<String>()
        val filnavn = mutableListOf<String>()
        val value = mutableListOf<ByteArray>()

        val vedleggServiceMock = mockk<VedleggService>().also {
            every { it.lagre(capture(soknadsid), capture(filnavn), capture(value)) } returns Unit
        }

        withMockAuthServerAndTestApplication({ vedleggApi(vedleggServiceMock) }) {
            autentisert(
                endepunkt = "v1/mellomlagring/soknadsId",
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
            }
        }

        verify(exactly = 2) { vedleggServiceMock.lagre(any(), any(), any()) }

        soknadsid shouldContainExactly (listOf("soknadsId", "soknadsId"))
        filnavn shouldContainExactly (listOf("file.csv", "file2.csv"))
        value.map(::String) shouldContainExactly (listOf("1", "2"))
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
    fun `Hente vedleggliste`() {
        val soknadsId = "soknadsId"
        val vedleggServiceMock = mockk<VedleggService>().also {
            every { it.hent(soknadsId) } returns listOf(
                VedleggMetadata("fil1"),
                VedleggMetadata("fil2")
            )
        }

        withMockAuthServerAndTestApplication({ vedleggApi(vedleggServiceMock) }) {
            autentisert(
                endepunkt = "v1/mellomlagring/$soknadsId",
                httpMethod = HttpMethod.Get
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
                //language=JSON
                response.content shouldBe """[{"filnavn":"fil1"},{"filnavn":"fil2"}]"""
            }
        }

        verify(exactly = 1) { vedleggServiceMock.hent(soknadsId) }
    }
}
