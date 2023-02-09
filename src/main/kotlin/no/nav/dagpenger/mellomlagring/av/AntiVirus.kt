package no.nav.dagpenger.mellomlagring.av

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.seconds

internal interface AntiVirus {
    suspend fun infisert(filnavn: String, filinnhold: ByteArray): Boolean
}

private val logger = KotlinLogging.logger { }

internal data class ScanResult(
    @JsonProperty("Filename")
    val fileName: String,
    @JsonProperty("Result")
    val result: String
) {
    fun infisert(): Boolean {
        return result.uppercase() != "OK"
    }
}

internal fun clamAv(engine: HttpClientEngine = CIO.create()): AntiVirus {
    return object : AntiVirus {
        private val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                jackson {
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60.seconds.inWholeMilliseconds
            }

            install(HttpRequestRetry) {
                retryIf(3) { _, response: HttpResponse ->
                    response.status.value.let { it in 400..599 }
                }
                exponentialDelay()
            }
        }

        override suspend fun infisert(filnavn: String, filinnhold: ByteArray): Boolean {
            return runCatching<List<ScanResult>> {
                httpClient.put {
                    url("http://clamav.nais-system.svc.cluster.local/scan")
                    setBody(ByteArrayInputStream(filinnhold))
                }.body()
            }.fold(
                onSuccess = {
                    require(it.isNotEmpty()) { "Skal ikke få tom liste fra clamv. Fil: $filnavn " }
                    logger.info { "Scannet fil $filnavn med resultat $it" }
                    it
                },
                onFailure = { t ->
                    logger.error(t) { "Fikk ikke scannet fil $filnavn: ${t.message}" }
                    throw t
                }
            ).any { it.infisert() }
        }
    }
}
