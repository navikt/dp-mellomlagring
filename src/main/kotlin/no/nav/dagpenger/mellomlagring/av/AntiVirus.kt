package no.nav.dagpenger.mellomlagring.av

import io.ktor.client.HttpClient
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.streams.asInput
import mu.KotlinLogging
import java.io.ByteArrayInputStream

internal interface AntiVirus {
    suspend fun infisert(filnavn: String, filinnhold: ByteArray): Boolean
}

private val logger = KotlinLogging.logger { }

object ClamAv : AntiVirus {
    private val httpClient = HttpClient() {
        install(JsonFeature) {
            serializer = JacksonSerializer {
            }
        }
        defaultRequest {
            host = "http://clamav.clamav.svc.cluster.local"
            header(HttpHeaders.ContentType, ContentType.MultiPart.FormData)
        }
    }

    private data class ScanResult(val filename: String, val result: String)

    override suspend fun infisert(filnavn: String, filinnhold: ByteArray): Boolean {
        val result: Result<ScanResult> =
            kotlin.runCatching {
                httpClient.post<ScanResult>() {
                    formData {
                        this.appendInput(
                            key = filnavn,
                        ) {
                            ByteArrayInputStream(filinnhold).asInput()
                        }
                    }
                }
            }
                .onSuccess { result ->
                    logger.info { "Scannet fil $filnavn med resultat $result" }
                }
                .onFailure { t ->
                    logger.error(t) { "Fikk ikke scannet fil: ${t.message}" }
                }
        return result.getOrThrow().result.uppercase() == "OK"
    }
}
