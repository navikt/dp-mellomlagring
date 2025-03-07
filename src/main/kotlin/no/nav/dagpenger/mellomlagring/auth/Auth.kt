package no.nav.dagpenger.mellomlagring.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.JWTConfigureFunction
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import kotlinx.coroutines.runBlocking
import java.net.URL
import java.util.concurrent.TimeUnit

fun ApplicationCall.azureAdEier(): String = this.request.header("X-Eier") ?: throw IllegalArgumentException("Request mangler X-Eier header")

fun ApplicationCall.oboEier(): String =
    this.principal<JWTPrincipal>()?.let {
        it["pid"]
    } ?: throw IllegalArgumentException("Fant ikke pid i jwt")

internal fun AuthenticationConfig.jwt(
    name: String,
    wellKnowUrl: String,
    configure: JWTConfigureFunction = {},
) {
    val openIDConfiguration: OpenIDConfiguration =
        runBlocking { httpClient.get(wellKnowUrl).body() }

    jwt(name) {
        verifier(
            jwkProvider = cachedJwkProvider(openIDConfiguration.jwksUri),
            issuer = openIDConfiguration.issuer,
            configure,
        )
        validate {
            val subject = it.payload.claims["pid"]?.asString() ?: it.payload.claims["sub"]?.asString()
            requireNotNull(subject) {
                "Token må inneholde pid"
            }
            JWTPrincipal(it.payload)
        }
    }
}

private data class OpenIDConfiguration(
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("issuer")
    val issuer: String,
)

private fun cachedJwkProvider(jwksUri: String): JwkProvider =
    JwkProviderBuilder(URL(jwksUri))
        .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
        .rateLimited(
            10,
            1,
            TimeUnit.MINUTES,
        ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .build()

private val httpClient =
    HttpClient {
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
