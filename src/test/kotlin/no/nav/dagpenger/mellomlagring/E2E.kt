@file:Suppress("ktlint")

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
import io.ktor.client.request.*
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.jackson.jackson
import io.ktor.util.toUpperCasePreservingASCIIRules
import io.kubernetes.client.openapi.ApiClient
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
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

// Hente azuread eller tokenx secret for  app
// jwker.nais.io -> tokenx,  azurerator.nais.io -> azuread
fun getAuthEnv(
    app: String,
    type: String = "jwker.nais.io",
): Map<String, String> {
    // file path to your KubeConfig
    val kubeConfigPath = System.getenv("KUBECONFIG")

    // IF this fails do kubectl get pod to aquire credentials
    val client: ApiClient = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(FileReader(kubeConfigPath))).build()

    return CoreV1Api(client)
        .listNamespacedSecret(
            "teamdagpenger",
        ).execute().items.filter {
            it.metadata.labels?.get("app") == app && it.metadata.labels?.get("type") == type
        }.first<V1Secret?>()
        ?.data!!
        .mapValues { e -> String(e.value) }
}


suspend fun getOboToken(
    app: String,
    selvbetjeningsIdToken: String,
): String {
    val tokenXConfig =
        OAuth2Config.TokenX(
            config = getAuthEnv(app),
        )
    val tokenClient =
        OAuth2Client(
            tokenEndpointUrl = tokenXConfig.tokenEndpointUrl,
            authType = tokenXConfig.privateKey(),
        )

    return tokenClient.tokenExchange(
        // 51818700273 -
        token = selvbetjeningsIdToken,
        audience = "dev-gcp:teamdagpenger:dp-mellomlagring",
    ).access_token!!
}

fun getAzureAdToken(app: String): String {
    val azureadConfig =
        OAuth2Config.AzureAd(
            getAuthEnv(app, "azurerator.nais.io"),
        )
    val tokenAzureAdClient: CachedOauth2Client by lazy {
        CachedOauth2Client(
            tokenEndpointUrl = azureadConfig.tokenEndpointUrl,
            authType = azureadConfig.clientSecret(),
        )
    }

    return tokenAzureAdClient.clientCredentials("api://dev-gcp.teamdagpenger.dp-mellomlagring/.default").access_token!!
}

val httpClientJackson =
    HttpClient {
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
val plainHttpClient =
    HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 130.seconds.inWholeMilliseconds
            connectTimeoutMillis = 130.seconds.inWholeMilliseconds
            socketTimeoutMillis = 130.seconds.inWholeMilliseconds
        }
    }

private data class BundleRequest(
    val bundleNavn: String,
    val soknadId: String,
    val filer: List<URN>,
) {
    data class URN(val urn: String)
}

private data class Response(
    val filnavn: String,
    val urn: String,
    val filsti: String,
    val storrelse: Long,
    val tidspunkt: ZonedDateTime,
) {
    fun nss(): String = URN.rfc8141().parse(urn).namespaceSpecificString().toString()
}

@ExtendWith(ParallelLoadExtension::class)
@Disabled
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

@Disabled
internal class E2E {
    private val eier = "51818700273"
    private val eier2 = "12345678910"

    // må erstattes om en skal sende inn filer på nytt
    private val soknadId = UUID.randomUUID().toString()

    // oboToken er tidsbegrenset, så det må erstattes med jevne mellomrom,
    // Hent fra https://tokenx-token-generator.intern.dev.nav.no/api/obo?aud=dev-gcp:teamdagpenger:dp-mellomlagring
    private val oboToken = ""


    @Test
    fun token() {
        val token = getAzureAdToken("dp-behov-soknad-pdf").also { println(it) }
    }

    @Test
    fun lastOppfil() {
        val token = getAzureAdToken("dp-behov-soknad-pdf")
        runBlocking {
            // Send filer til mellomlagring
            log("Send filer til mellomlagring")
            val httpResponse = httpClientJackson.submitFormWithBinaryData(
                url = "https://dp-mellomlagring.intern.dev.nav.no/v1/azuread/mellomlagring/vedlegg/oppgave/oppgaveId/type_brev",
                formData =
                    formData {
                        append(
                            "image",
                            "/smallimg1.jpg".fileAsByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"æ ø å.jpg\"")
                            },
                        )
                    },
            ) {
                this.header("Authorization", "Bearer $token")
                this.header("X-Eier", value = eier)
            }
            println(httpResponse.body<List<Response>>())
        }
    }

    @Disabled
    @Test
    fun volume() {
        println("Running test with id: $soknadId and eier $eier")
        val fileAsByteArray = "/middlesize.jpg".fileAsByteArray()
        val formData =
            formData {
                repeat(2) { n ->
                    append(
                        "image",
                        fileAsByteArray,
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"$n.jpg\"")
                        },
                    )
                }
            }
        runBlocking {
            // Send filer til mellomlagring
            val responseList =
                measure("Tid brukt på å sender opp filer") {
                    return@measure httpClientJackson.submitFormWithBinaryData(
                        url = "https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId/fakta1",
                        formData = formData,
                    ) {
                        this.header("Authorization", "Bearer $oboToken")
                    }.body<List<Response>>()
                }

            // Bundle filer
            measure("Tid brukt for å bundle") {
                responseList.map { it.urn }
                httpClientJackson.post("https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/pdf/bundle") {
                    header("Authorization", "Bearer $oboToken")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(
                        BundleRequest(
                            bundleNavn = "bundle.pdf",
                            soknadId = soknadId,
                            filer = responseList.map { BundleRequest.URN(it.urn) },
                        ),
                    )
                }.body<Response>()
            }.also { println(it) }
        }
    }

    private inline fun <T : Any> measure(
        msg: String,
        block: () -> T,
    ): T {
        var t: T
        measureTime {
            t = block()
        }.also { println("$msg: $it") }
        return t
    }

    private fun log(msg: String) {
        println("************ ${msg.toUpperCasePreservingASCIIRules()} ************")
    }

    @Disabled
    @Test
    fun e2e() {
        runBlocking {
            log("Running test with id: $soknadId and eier $eier")

            // Send ugyldig filer
            log("sender ugyldige filer")
            listOf("test.txt", "protected.pdf").forEach { fil ->
                httpClientJackson.submitFormWithBinaryData(
                    url = "https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId/fakta1",
                    formData =
                        formData {
                            append(
                                "text",
                                "/$fil".fileAsByteArray(),
                                Headers.build {
                                    append(HttpHeaders.ContentType, "text/plain")
                                    append(HttpHeaders.ContentDisposition, "filename=\"$fil\"")
                                },
                            )
                        },
                ) {
                    this.header("Authorization", "Bearer $oboToken")
                }.let {
                    it.status shouldBe HttpStatusCode.BadRequest
                    println(it.bodyAsText())
                }
            }

            // Send filer til mellomlagring
            log("Send filer til mellomlagring")
            httpClientJackson.submitFormWithBinaryData(
                url = "https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId/fakta1",
                formData =
                    formData {
                        append(
                            "image",
                            "/smallimg1.jpg".fileAsByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"æ ø å.jpg\"")
                            },
                        )
                    },
            ) {
                this.header("Authorization", "Bearer $oboToken")
            }.body<List<Response>>().also { println(it) }.size shouldBe 1

            httpClientJackson.submitFormWithBinaryData(
                url = "https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId/fakta2",
                formData =
                    formData {
                        append(
                            "image",
                            "/Arbeidsforhold2.pdf".fileAsByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"Arbeidsforhold.pdf\"")
                            },
                        )
                    },
            ) {
                this.header("Authorization", "Bearer $oboToken")
            }.body<List<Response>>().also { println(it) }.size shouldBe 1

            // List alle filer
            log("list alle filer")
            val listResponse =
                httpClientJackson.get("https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId") {
                    this.header("Authorization", "Bearer $oboToken")
                }.body<List<Response>>().also { println(it) }

            // Hent en fil
            log("Hent en file")
            val id1 = listResponse.first { it.filnavn == "æ ø å.jpg" }.nss()
            httpClientJackson.get("https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/vedlegg/$id1") {
                this.header("Authorization", "Bearer $oboToken")
            }.also { response ->
                println(response)
                File("build/tmp/download.jpg").appendBytes(response.body())
            }

            // hente en fil med azuread
            log("Hent en fil med azuread")
            val azureadToken = getAzureAdToken("dp-behov-soknad-pdf")
            val id2 = listResponse.first { it.filnavn == "Arbeidsforhold.pdf" }.nss()
            httpClientJackson.get("https://dp-mellomlagring.intern.dev.nav.no/v1/azuread/mellomlagring/vedlegg/$id2") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.also { response ->
                println(response)
                File("build/tmp/download.pdf").appendBytes(response.body())
            }

            // Bundle filer
            log("Bundle filer")
            val bundleResponse =
                plainHttpClient.post("https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/pdf/bundle") {
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
                    """,
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
            log("Hente bundle")
            httpClientJackson.get("https://dp-mellomlagring.intern.dev.nav.no/v1/azuread/mellomlagring/vedlegg/$bundleId") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.also { response ->
                println(response)
                File("build/tmp/bundle.pdf").appendBytes(response.body())
            }

            // Kan ikke slette bundle dersom man ikke eier fil
            log("Kan ikke slette bundle dersom man ikke eier fil")
            httpClientJackson.delete("https://dp-mellomlagring.intern.dev.nav.no/v1/azuread/mellomlagring/vedlegg/$bundleId") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier2)
            }.let { response ->
                response.status shouldBe HttpStatusCode.Forbidden
            }

            // Kan slette filer med riktig eier.
            // azure
            log("Kan slette filer med riktig eier")
            httpClientJackson.delete("https://dp-mellomlagring.intern.dev.nav.no/v1/azuread/mellomlagring/vedlegg/$bundleId") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.let {
                it.status shouldBe HttpStatusCode.NoContent
            }

            // azure
            log("Kan slette filer med riktig eier azure")
            httpClientJackson.delete("https://dp-mellomlagring.intern.dev.nav.no/v1/azuread/mellomlagring/vedlegg/$id1") {
                this.header("Authorization", "Bearer $azureadToken")
                this.header("X-Eier", value = eier)
            }.let {
                it.status shouldBe HttpStatusCode.NoContent
            }

            // obo
            log("Kan slette filer med riktig eier obo")
            httpClientJackson.delete("https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/vedlegg/$id2") {
                this.header("Authorization", "Bearer $oboToken")
            }.let {
                it.status shouldBe HttpStatusCode.NoContent
            }

            // List alle filer
            log("List alle filer. Burde være tomt")
            httpClientJackson.get("https://dp-mellomlagring.intern.dev.nav.no/v1/obo/mellomlagring/vedlegg/$soknadId") {
                this.header("Authorization", "Bearer $oboToken")
            }.body<List<Response>>().also { println(it) }
        }
    }
}