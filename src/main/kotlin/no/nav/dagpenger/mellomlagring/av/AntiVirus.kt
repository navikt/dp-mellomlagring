package no.nav.dagpenger.mellomlagring.av

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.streams.asInput
import mu.KotlinLogging
import java.io.ByteArrayInputStream

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
        }

        override suspend fun infisert(filnavn: String, filinnhold: ByteArray): Boolean {
            return runCatching<List<ScanResult>> {
                httpClient.submitFormWithBinaryData(
                    url = "http://clamav.clamav.svc.cluster.local/scan",
                    formData = formData {
                        appendInput(
                            key = filnavn,
                            headers = Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=$filnavn")
                            },
                        ) {
                            ByteArrayInputStream(filinnhold).asInput()
                        }
                    }
                ).body()
            }.fold(
                onSuccess = {
                    logger.info { "Scannet fil $filnavn med resultat $it" }
                    it
                },
                onFailure = { t ->
                    logger.error(t) { "Fikk ikke scannet fil: ${t.message}" }
                    throw t
                }
            ).any { it.infisert() }
        }
    }
}
