package no.nav.dagpenger.mellomlagring

import io.ktor.application.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.security.mock.oauth2.MockOAuth2Server

internal object TestApplication {
    const val defaultDummyFodselsnummer = "12345"

    private val mockOAuth2Server: MockOAuth2Server by lazy {
        MockOAuth2Server().also { server ->
            server.start()
        }
    }

    private val testOAuthToken: String by lazy {
        mockOAuth2Server.issueToken(
            issuerId = Config.tokenxIssuerName,
            claims = mapOf(
                "sub" to defaultDummyFodselsnummer,
                "aud" to "audience"
            )
        ).serialize()
    }

    internal fun <R> withMockAuthServerAndTestApplication(
        moduleFunction: Application.() -> Unit,
        test: TestApplicationEngine.() -> R
    ): R {
        try {
            System.setProperty("TOKEN_X_WELL_KNOWN_URL", "${mockOAuth2Server.wellKnownUrl(Config.tokenxIssuerName)}")

            return withTestApplication(moduleFunction, test)
        } finally {
        }
    }

    internal fun TestApplicationEngine.autentisert(
        endepunkt: String,
        token: String = testOAuthToken,
        httpMethod: HttpMethod = HttpMethod.Get,
        setup: TestApplicationRequest.() -> Unit = {}
    ) = handleRequest(httpMethod, endepunkt) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        setup()
    }
}
