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
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.mellomlagring.lagring.VedleggMetadata
import no.nav.dagpenger.mellomlagring.lagring.VedleggService
import org.junit.jupiter.api.Test

internal class LagringTest {
    @Test
    fun `Lagring av fil`() {
        val soknadsid = mutableListOf<String>()
        val filnavn = mutableListOf<String>()
        val value = mutableListOf<ByteArray>()

        val vedleggServiceMock = mockk<VedleggService>().also {
            every { it.lagre(capture(soknadsid), capture(filnavn), capture(value)) } returns Unit
        }

        withTestApplication({ vedleggApi(vedleggServiceMock) }) {
            handleRequest(HttpMethod.Post, "v1/mellomlagring/soknadsId") {
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
        withTestApplication({ vedleggApi(mockk()) }) {
            handleRequest(HttpMethod.Post, "v1/mellomlagring/").apply {
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

        withTestApplication({ vedleggApi(vedleggServiceMock) }) {

            handleRequest(HttpMethod.Get, "v1/mellomlagring/$soknadsId").apply {
                response.status() shouldBe HttpStatusCode.OK
                //language=JSON
                response.content shouldBe """[{"filnavn":"fil1"},{"filnavn":"fil2"}]"""
            }
        }

        verify(exactly = 1) { vedleggServiceMock.hent(soknadsId) }
    }
}
