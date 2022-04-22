package no.nav.dagpenger.mellomlagring

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.testApplication
import no.nav.security.mock.oauth2.MockOAuth2Server

internal object TestApplication {
    const val defaultDummyFodselsnummer = "123456789"

    private val mockOAuth2Server: MockOAuth2Server by lazy {
        MockOAuth2Server().also { server ->
            server.start()
        }
    }

    internal val tokenXToken: String by lazy {
        mockOAuth2Server.issueToken(
            issuerId = Config.TokenX.name,
            claims = mapOf(
                "sub" to defaultDummyFodselsnummer,
                "aud" to Config.TokenX.audience
            )
        ).serialize()
    }

    internal val azureAd: String by lazy {
        mockOAuth2Server.issueToken(
            issuerId = Config.AzureAd.name,
            claims = mapOf(
                "aud" to Config.AzureAd.audience
            )
        ).serialize()
    }

    internal fun withMockAuthServerAndTestApplication(
        moduleFunction: Application.() -> Unit,
        test: suspend ApplicationTestBuilder.() -> Unit
    ) {
        try {
            System.setProperty("TOKEN_X_WELL_KNOWN_URL", "${mockOAuth2Server.wellKnownUrl(Config.TokenX.name)}")
            System.setProperty("AZURE_APP_WELL_KNOWN_URL", "${mockOAuth2Server.wellKnownUrl(Config.AzureAd.name)}")
            testApplication {
                application(moduleFunction)
                test()
            }
        } finally {
        }
    }

    internal fun HttpRequestBuilder.autentisert(token: String = tokenXToken) {
        this.header(HttpHeaders.Authorization, "Bearer $token")
    }

    internal fun TestApplicationEngine.autentisert(
        endepunkt: String,
        token: String = tokenXToken,
        httpMethod: HttpMethod = HttpMethod.Get,
        setup: TestApplicationRequest.() -> Unit = {}
    ): TestApplicationCall = handleRequest(httpMethod, endepunkt) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        setup()
    }
}
