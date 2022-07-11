package no.nav.dagpenger.mellomlagring

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import java.util.UUID

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

val httpClient = HttpClient { }

internal class E2E {
    val eier = "51818700273"

    // må erstattes om en skal sende inn filer på nytt
    val soknadId = UUID.randomUUID().toString()

    // selvbetjeningstoken er tidsbegrenset, så det må erstattes med jevne mellomrom, logg inn med eier 51818700273
    val selvbetjeningsIdToken = ""

    @Disabled
    @Test
    fun e2e() {
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
                        "image", "/smallimg1.jpg".fileAsByteArray(),
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"smallimg.jpg\"")
                        }
                    )
                    append(
                        "image", "/Arbeidsforhold2.pdf".fileAsByteArray(),
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
                        "soknadId": "$soknadId",
                        "filer": [
                          {"urn": "urn:vedlegg:$soknadId/smallimg.jpg"},
                          {"urn": "urn:vedlegg:$soknadId/Arbeidsforhold.pdf"}
                        ]   
                      }
                    """
                    )
                }.also { println(it.bodyAsText()) }
            bundleResponse.status shouldBe HttpStatusCode.Created

            // hente bundle
            httpClient.get("https://dp-mellomlagring.dev.intern.nav.no/v1/azuread/mellomlagring/vedlegg/$soknadId/bundle.pdf") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.also { response ->
                println(response)
                File("build/tmp/bundle.pdf").appendBytes(response.body())
            }
        }
    }

    @Disabled
    @Test
    fun `azuretilatte operasjoner`() {
        runBlocking {
            val azureadToken = getAzureAdToken("dp-behov-soknad-pdf")
            httpClient.get("https://dp-mellomlagring.dev.intern.nav.no/v1/azuread/mellomlagring/vedlegg/$soknadId/Arbeidsforhold.pdf") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.also { response ->
                println(response)
                File("build/tmp/download.pdf").appendBytes(response.body())
            }
        }
    }

    @Disabled
    @Test
    fun `e2e bundle filer`() {
        runBlocking {

            // Bundle filer
            val azureadToken = getAzureAdToken("dp-behov-soknad-pdf")
            val bundleResponse =
                httpClient.post("https://dp-mellomlagring.dev.intern.nav.no/v1/mellomlagring/pdf/bundle") {
                    this.header("Authorization", "Bearer $azureadToken")
                    this.header("X-Eier", value = eier)
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(
                        """
                      { 
                        "bundleNavn": "bundle1.pdf",
                        "soknadId": "$soknadId",
                        "filer": [
                          {"urn": "urn:vedlegg:$soknadId/smallimg.jpg"},
                          {"urn": "urn:vedlegg:$soknadId/Arbeidsforhold.pdf"}
                        ]   
                      }
                    """
                    )
                }.also { println(it) }

            println(bundleResponse.bodyAsText())
            bundleResponse.status shouldBe HttpStatusCode.Created
        }
    }
}
