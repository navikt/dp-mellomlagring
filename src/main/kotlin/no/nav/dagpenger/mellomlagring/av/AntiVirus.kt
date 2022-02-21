package no.nav.dagpenger.mellomlagring.av

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.streams.asInput
import mu.KotlinLogging
import java.io.ByteArrayInputStream

internal interface AntiVirus {
    suspend fun infisert(filnavn: String, filinnhold: ByteArray): Boolean
}

private val logger = KotlinLogging.logger { }

internal data class ScanResult(val Filename: String, val Result: String) {
    fun harVirus(): Boolean {
        return Result.uppercase() != "OK"
    }
}

internal fun clamAv(engine: HttpClientEngine = CIO.create()): AntiVirus {
    return object : AntiVirus {
        private val httpClient = HttpClient(engine) {
            install(JsonFeature) {
                serializer = JacksonSerializer {
                }
            }
        }

        override suspend fun infisert(filnavn: String, filinnhold: ByteArray): Boolean {
            val result =
                kotlin.runCatching {
                    httpClient.submitFormWithBinaryData<List<ScanResult>>(
                        url = "http://clamav.clamav.svc.cluster.local/scan",
                        formData = formData {
                            this.appendInput(
                                key = filnavn,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentDisposition, "filename=$filnavn")
                                },
                            ) {
                                ByteArrayInputStream(filinnhold).asInput()
                            }
                        }
                    )
                }
                    .onSuccess { result ->
                        logger.info { "Scannet fil $filnavn med resultat $result" }
                    }
                    .onFailure { t ->
                        logger.error(t) { "Fikk ikke scannet fil: ${t.message}" }
                    }

            return result.getOrThrow().any { scanResult ->
                scanResult.harVirus()
            }
        }
    }

}

