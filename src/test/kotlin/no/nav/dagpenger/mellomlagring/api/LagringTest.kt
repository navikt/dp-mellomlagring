package no.nav.dagpenger.mellomlagring.api

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.mellomlagring.lagring.Store
import no.nav.dagpenger.mellomlagring.lagring.VedleggMetadata
import org.junit.jupiter.api.Test

internal class LagringTest {
    @Test
    fun `Lagring av fil`() {
        val mockStore = mockk<Store>().also {
            every { it.lagre(any()) } returns Unit
        }

        withTestApplication({ store(mockStore) }) {
            handleRequest(HttpMethod.Post, "v1/mellomlagring/soknadsId").apply {
                response.status() shouldBe HttpStatusCode.Created
            }
        }

        verify(exactly = 1) { mockStore.lagre(any()) }
    }

    @Test
    fun `Lagring krever søknads id`() {
        withTestApplication({ store(mockk()) }) {
            handleRequest(HttpMethod.Post, "v1/mellomlagring/").apply {
                response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `Hente vedleggliste`() {
        val soknadsId = "soknadsId"
        val mockStore = mockk<Store>().also {
            every { it.hent(soknadsId) } returns listOf(
                VedleggMetadata(soknadsId, "fil1"),
                VedleggMetadata(soknadsId, "fil2")
            )
        }

        withTestApplication({ store(mockStore) }) {

            handleRequest(HttpMethod.Get, "v1/mellomlagring/$soknadsId").apply {
                response.status() shouldBe HttpStatusCode.OK
                //language=JSON
                response.content shouldBe """[{"soknadsId":"soknadsId","filnavn":"fil1"},{"soknadsId":"soknadsId","filnavn":"fil2"}]"""
            }
        }

        verify(exactly = 1) { mockStore.hent(soknadsId) }
    }
}
