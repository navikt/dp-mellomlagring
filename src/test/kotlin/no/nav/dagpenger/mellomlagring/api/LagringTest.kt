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
import org.junit.jupiter.api.Test

internal class LagringTest {
    @Test
    fun `Lagring av fil`() {
        val hubba = Store.Hubba("soknadsId")
        val mockStore = mockk<Store>().also {
            every { it.lagre(hubba) } returns Unit
        }

        withTestApplication({ store(mockStore) }) {
            handleRequest(HttpMethod.Post, "v1/mellomlagring/soknadsId").apply {
                response.status() shouldBe HttpStatusCode.Created
            }
        }

        verify(exactly = 1) { mockStore.lagre(hubba) }
    }
}


