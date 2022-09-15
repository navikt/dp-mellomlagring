package no.nav.dagpenger.mellomlagring

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.slub.urn.URN
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
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
import org.jsmart.zerocode.core.domain.LoadWith
import org.jsmart.zerocode.core.domain.TestMapping
import org.jsmart.zerocode.core.domain.TestMappings
import org.jsmart.zerocode.jupiter.extension.ParallelLoadExtension
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.io.FileReader
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

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
    ).accessToken
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

    return tokenAzureAdClient.clientCredentials("api://dev-gcp.teamdagpenger.dp-mellomlagring/.default").accessToken
}

val httpClientJackson = HttpClient {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 130.seconds.inWholeMilliseconds
        connectTimeoutMillis = 130.seconds.inWholeMilliseconds
        socketTimeoutMillis = 130.seconds.inWholeMilliseconds
    }
}
val plainHttpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 130.seconds.inWholeMilliseconds
        connectTimeoutMillis = 130.seconds.inWholeMilliseconds
        socketTimeoutMillis = 130.seconds.inWholeMilliseconds
    }
}

private data class BundleRequest(
    val bundleNavn: String,
    val soknadId: String,
    val filer: List<URN>
) {
    data class URN(val urn: String)
}

private data class Response(
    val filnavn: String,
    val urn: String,
    val filsti: String,
    val storrelse: Long,
    val tidspunkt: ZonedDateTime
) {
    private val _urn = URN.rfc8141().parse(urn)
    fun nss(): String = _urn.namespaceSpecificString().toString()
}

@ExtendWith(ParallelLoadExtension::class)
internal class E2ELoad {
    @Test
    @DisplayName("Load test E2E")
    @LoadWith("load_generation.properties")
    @TestMappings(
        TestMapping(testClass = E2E::class, testMethod = "e2e"),
    )
    @Disabled
    fun testLoad() {
        // This space remains empty
    }
}

internal class E2E {
    val eier = "51818700273"
    val eier2 = "12345678910"

    // må erstattes om en skal sende inn filer på nytt
    val soknadId = UUID.randomUUID().toString()

    // selvbetjeningstoken er tidsbegrenset, så det må erstattes med jevne mellomrom,
    // logg inn på søknaden i dev med eier 51818700273 og kopier selvbetjening-token fra devtools ->Appilcation->Storage
    val selvbetjeningsIdToken = ""

    @Disabled
    @Test
    fun volume() {
        println("Running test with id: $soknadId and eier $eier")
        val fileAsByteArray = "/middlesize.jpg".fileAsByteArray()
        val formData = formData {
            repeat(2) { n ->
                append(
                    "image", fileAsByteArray,
                    Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"$n.jpg\"")
                    }
                )
            }
        }
        runBlocking {
            val oboToken = getOboToken(
                "dp-soknadsdialog",
                selvbetjeningsIdToken
            )
            // Send filer til mellomlagring
            val responseList = measure("Tid brukt på å sender opp filer") {
                return@measure httpClientJackson.submitFormWithBinaryData(
                    url = "https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId/fakta1",
                    formData = formData
                ) {
                    this.header("Authorization", "Bearer $oboToken")
                }.body<List<Response>>()
            }

            // Bundle filer
            val bundleResponse = measure("Tid brukt for å bundle") {
                responseList.map { it.urn }
                httpClientJackson.post("https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/pdf/bundle") {
                    header("Authorization", "Bearer $oboToken")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(
                        BundleRequest(
                            bundleNavn = "bundle.pdf",
                            soknadId = soknadId,
                            filer = responseList.map { BundleRequest.URN(it.urn) }
                        )
                    )
                }.body<Response>()
            }.also { println(it) }
        }
    }

    @OptIn(ExperimentalTime::class)
    private inline fun <T : Any> measure(msg: String, block: () -> T): T {
        var t: T
        measureTime {
            t = block()
        }.also { println("$msg: $it") }
        return t
    }

    @Disabled
    @Test
    fun e2e() {
        runBlocking {

            println("Running test with id: $soknadId and eier $eier")
            val oboToken = getOboToken(
                "dp-soknadsdialog",
                selvbetjeningsIdToken
            )

            // Send ugyldig filer
            listOf("test.txt", "protected.pdf").forEach { fil ->
                httpClientJackson.submitFormWithBinaryData(
                    url = "https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId/fakta1",
                    formData = formData {
                        append(
                            "text", "/$fil".fileAsByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, "text/plain")
                                append(HttpHeaders.ContentDisposition, "filename=\"$fil\"")
                            }
                        )
                    }
                ) {
                    this.header("Authorization", "Bearer $oboToken")
                }.let {
                    it.status shouldBe HttpStatusCode.BadRequest
                    println(it.bodyAsText())
                }
            }

            // Send filer til mellomlagring
            httpClientJackson.submitFormWithBinaryData(
                url = "https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId/fakta1",
                formData = formData {
                    append(
                        "image", "/smallimg1.jpg".fileAsByteArray(),
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"æ ø å.jpg\"")
                        }
                    )
                }
            ) {
                this.header("Authorization", "Bearer $oboToken")
            }.body<List<Response>>().also { println(it) }.size shouldBe 1

            httpClientJackson.submitFormWithBinaryData(
                url = "https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId/fakta2",
                formData = formData {
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
            }.body<List<Response>>().also { println(it) }.size shouldBe 1

            // List alle filer
            val listResponse =
                httpClientJackson.get("https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId") {
                    this.header("Authorization", "Bearer $oboToken")
                }.body<List<Response>>().also { println(it) }

            // Hent en fil
            val id1 = listResponse.first { it.filnavn == "æ ø å.jpg" }.nss()
            httpClientJackson.get("https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$id1") {
                this.header("Authorization", "Bearer $oboToken")
            }.also { response ->
                println(response)
                File("build/tmp/download.jpg").appendBytes(response.body())
            }

            // hente en fil med azuread
            val azureadToken = getAzureAdToken("dp-behov-soknad-pdf")
            val id2 = listResponse.first { it.filnavn == "Arbeidsforhold.pdf" }.nss()
            httpClientJackson.get("https://dp-mellomlagring.dev.intern.nav.no/v1/azuread/mellomlagring/vedlegg/$id2") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.also { response ->
                println(response)
                File("build/tmp/download.pdf").appendBytes(response.body())
            }

            // Bundle filer
            val bundleResponse =
                plainHttpClient.post("https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/pdf/bundle") {
                    header("Authorization", "Bearer $oboToken")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(
                        """
                      { 
                        "bundleNavn": "bundle.pdf",
                        "soknadId": "$soknadId",
                        "filer": [
                          {"urn": "urn:vedlegg:$id1"},
                          {"urn": "urn:vedlegg:$id2"}
                        ]   
                      }
                    """
                    )
                }
            bundleResponse.status shouldBe HttpStatusCode.Created
            val bodyAsText = bundleResponse.bodyAsText().also { println(it) }
            val bundleId =
                jacksonObjectMapper().also {
                    it.registerModule(JavaTimeModule())
                    it.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }.readValue(bodyAsText, Response::class.java).also { println(it) }
                    .nss()

            // hente bundle
            httpClientJackson.get("https://dp-mellomlagring.dev.intern.nav.no/v1/azuread/mellomlagring/vedlegg/$bundleId") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.also { response ->
                println(response)
                File("build/tmp/bundle.pdf").appendBytes(response.body())
            }

            // Kan ikke slette bundle dersom man ikke eier fil
            httpClientJackson.delete("https://dp-mellomlagring.dev.intern.nav.no/v1/azuread/mellomlagring/vedlegg/$bundleId") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier2)
            }.let { response ->
                response.status shouldBe HttpStatusCode.Forbidden
            }

            // Kan slette filer med riktig eier.
            // azure
            httpClientJackson.delete("https://dp-mellomlagring.dev.intern.nav.no/v1/azuread/mellomlagring/vedlegg/$bundleId") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.let {
                it.status shouldBe HttpStatusCode.NoContent
            }

            // azure
            httpClientJackson.delete("https://dp-mellomlagring.dev.intern.nav.no/v1/azuread/mellomlagring/vedlegg/$id1") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.let {
                it.status shouldBe HttpStatusCode.NoContent
            }

            // obo
            httpClientJackson.delete("https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$id2") {
                this.header("Authorization", "Bearer $oboToken")
            }.let {
                it.status shouldBe HttpStatusCode.NoContent
            }

            // List alle filer
            httpClientJackson.get("https://dp-mellomlagring.dev.intern.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId") {
                this.header("Authorization", "Bearer $oboToken")
            }.body<List<Response>>().also { println(it) }
        }
    }
}
