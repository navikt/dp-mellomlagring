package no.nav.dagpenger.mellomlagring

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Secret
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.KubeConfig
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.mellomlagring.test.fileAsByteArray
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Client
import no.nav.dagpenger.oauth2.OAuth2Config
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileReader
import java.time.OffsetDateTime

// Hente azuread eller tokenx secret for  app
// jwker.nais.io -> tokenx,  azurerator.nais.io -> azuread
fun getAuthEnv(app: String, type: String = "jwker.nais.io"): Map<String, String> {
    // file path to your KubeConfig
    val kubeConfigPath = System.getenv("KUBECONFIG")

    // IF this fails do kubectl get pod to aquire credentials
    val client: ApiClient = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(FileReader(kubeConfigPath))).build()
    Configuration.setDefaultApiClient(client)
    return CoreV1Api().listNamespacedSecret(
        "teamdagpenger",
        null,
        null,
        null,
        null,
        "app=$app,type=$type",
        null,
        null,
        null,
        null,
        null
    ).items.also { secrets ->
        secrets.sortByDescending<V1Secret?, OffsetDateTime> { it?.metadata?.creationTimestamp }
    }.first<V1Secret?>()?.data!!.mapValues { e -> String(e.value) }
}

suspend fun getOboToken(app: String, selvbetjeningsIdToken: String): String {
    val tokenXConfig = OAuth2Config.TokenX(
        config = getAuthEnv(app),
    )
    val tokenClient = OAuth2Client(
        tokenEndpointUrl = tokenXConfig.tokenEndpointUrl,
        authType = tokenXConfig.privateKey(),
    )

    return tokenClient.tokenExchange(
        // 51818700273 -
        token = selvbetjeningsIdToken,
        audience = "dev-gcp:teamdagpenger:dp-mellomlagring"
    ).accessToken.also { println(it) }
}

fun getAzureAdToken(app: String): String {
    val azureadConfig = OAuth2Config.AzureAd(
        getAuthEnv(app, "azurerator.nais.io")
    )
    val tokenAzureAdClient: CachedOauth2Client by lazy {
        CachedOauth2Client(
            tokenEndpointUrl = azureadConfig.tokenEndpointUrl,
            authType = azureadConfig.clientSecret()
        )
    }

    return tokenAzureAdClient.clientCredentials("api://dev-gcp.teamdagpenger.dp-mellomlagring/.default").accessToken.also {
        println(it)
    }
}

val httpClient = HttpClient {
    install(ContentNegotiation) {
        jackson {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }
    }
}

internal class E2E {
    @Test
    @Disabled
    fun e2e() {
        // todo fixme to run test
        val soknadId = "51818700273"
        val eier = "373bf525-ed7b-4307-ad0c-f34981cc3f07"
        val selvbetjeningsIdToken = "eyJraWQiOiJ2UHBaZW9HOGRkTHpmdHMxLWxnc3VnOHNyYVd3bW04dHhJaGJ3Y1h3R01JIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJOTm13dDAwY2EyZldxN2ZXdS1TYVRwUDZEbUNYS0g2VTVsV3hINjhkLU9VPSIsImlzcyI6Imh0dHBzOlwvXC9vaWRjLXZlcjIuZGlmaS5ub1wvaWRwb3J0ZW4tb2lkYy1wcm92aWRlclwvIiwiY2xpZW50X2FtciI6ImNsaWVudF9zZWNyZXRfcG9zdCIsInBpZCI6IjUxODE4NzAwMjczIiwidG9rZW5fdHlwZSI6IkJlYXJlciIsImNsaWVudF9pZCI6ImQ4Mjk3MjQwLTIzYzgtNDBjOC1iMWM4LWEyOTNhZjczNjk3MiIsImF1ZCI6Imh0dHBzOlwvXC9uYXYubm8iLCJhY3IiOiJMZXZlbDQiLCJzY29wZSI6Im9wZW5pZCIsImV4cCI6MTY1NzUzNDMzOCwiaWF0IjoxNjU3NTMwNzM4LCJjbGllbnRfb3Jnbm8iOiI4ODk2NDA3ODIiLCJqdGkiOiJzbTBPMDllZ0twMXU2VDlFYUZxZ0VITTNOVDhIdy1GWjZ3NVZVelp6Wl9ZIiwiY29uc3VtZXIiOnsiYXV0aG9yaXR5IjoiaXNvNjUyMy1hY3RvcmlkLXVwaXMiLCJJRCI6IjAxOTI6ODg5NjQwNzgyIn19.ZLPIQlFRjSie4eDvVPnHcbIgMeaj_06NeTwLgwh_reiAtW6So-iXZELovOkSpBzuy6CB0SDdvUt24CO6vi9Cm0XAw-1b6h3qac3iYy_txQ-2Id-zYpSdOz-XXkKKtzot81AZqUlGXdiMpE6_L6zbs70PdQisrsAVcm81ltfE3sn4qTSIWNcP0NN_8ioydbjs7DEBmGzYRbKNyqkLO86D-LuimxuxJfp_hHF38nNgOq2EhJdkLXFO-1fm14Ni9S661wqHLtvRgtZk4dsW7WL68ZrhMez1ZX3OJ8fUzn9ItSUHqT_u5rwc0l6nzuIqe0LeWyPlUGxtnu2j119gtFU6tg"

        runBlocking {
            val oboToken = getOboToken(
                "dp-soknadsdialog",
                selvbetjeningsIdToken
            )

            // Send filer til mellomlagring
            httpClient.submitFormWithBinaryData(
                url = "https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId",
                formData = formData {
                    append(
                        "image", "/smallimg.jpg".fileAsByteArray(),
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"smallimg.jpg\"")
                        }
                    )
                    append(
                        "image", "/Arbeidsforhold.pdf".fileAsByteArray(),
                        Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"Arbeidsforhold.pdf\"")
                        }
                    )
                }
            ) {
                this.header("Authorization", "Bearer $oboToken")
            }.also { println(it.bodyAsText()) }

            // List alle filer
            httpClient.get("https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId") {
                this.header("Authorization", "Bearer $oboToken")
            }.also { println("GET ${it.bodyAsText()}") }

            // Hent en fil
            httpClient.get("https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId/smallimg.jpg") {
                this.header("Authorization", "Bearer $oboToken")
            }.also { response ->
                println(response)
                File("build/tmp/download.jpg").appendBytes(response.body())
            }

            // hente en fil med azuread
            val azureadToken = getAzureAdToken("dp-behov-soknad-pdf")
            httpClient.get("https://dp-mellomlagring.dev.intern.nav.no/v1/azuread/mellomlagring/vedlegg/$soknadId/Arbeidsforhold.pdf") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.also { response ->
                println(response)
                File("build/tmp/download.pdf").appendBytes(response.body())
            }

            // Bundle filer
            val bundleResponse =
                httpClient.post("https://dp-mellomlagring.dev.intern.nav.no/v1/mellomlagring/pdf/bundle") {
                    header("Authorization", "Bearer $azureadToken")
                    header("X-Eier", value = eier)
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(
                        """
                      { 
                        "bundleNavn": "bundle.pdf",
                        "soknadId": $soknadId,
                        "filer": [
                          {"urn": "urn:vedlegg:$soknadId/smallimg.jpg"},
                          {"urn": "urn:vedlegg:$soknadId/Arbeidsforhold.pdf"}
                        ]   
                      }
                    """
                    )
                }.also { println(it) }

            bundleResponse.status shouldBe HttpStatusCode.Created
        }
    }
}
