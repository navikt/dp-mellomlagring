package no.nav.dagpenger.mellomlagring.av

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
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
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.dagpenger.ktor.client.metrics.PrometheusMetricsPlugin
import no.nav.dagpenger.mellomlagring.monitoring.Metrics
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.seconds

internal interface AntiVirus {
    suspend fun infisert(
        filnavn: String,
        filinnhold: ByteArray,
    ): Boolean
}

private val logger = KotlinLogging.logger { }

internal data class ScanResult(
    @JsonProperty("Filename")
    val fileName: String,
    @JsonProperty("Result")
    val result: String,
) {
    fun infisert(): Boolean = result.uppercase() != "OK"
}

internal fun clamAv(
    engine: HttpClientEngine = CIO.create(),
    registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
): AntiVirus =
    object : AntiVirus {
        private val httpClient =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    jackson {
                    }
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30.seconds.inWholeMilliseconds
                }

                install(PrometheusMetricsPlugin) {
                    this.baseName = "dp_mellomlagring_clamav_client"
                    this.registry = registry
                }

                install(HttpRequestRetry) {
                    retryIf(3) { _, response: HttpResponse ->
                        response.status.value.let { it in 400..599 }
                    }
                    exponentialDelay()
                }
            }

        private fun List<ScanResult>.registerMetrics() {
            this.forEach {
                Metrics.antivirusResultCounter.labelValues(it.result).inc()
            }
        }

        override suspend fun infisert(
            filnavn: String,
            filinnhold: ByteArray,
        ): Boolean =
            runCatching<List<ScanResult>> {
                httpClient
                    .put {
                        url("http://clamav.nais-system.svc.cluster.local/scan")
                        setBody(ByteArrayInputStream(filinnhold))
                    }.body()
            }.fold(
                onSuccess = {
                    require(it.isNotEmpty()) { "Skal ikke få tom liste fra clamv.  " }
                    logger.info { "Scannet fil med resultat $it" }
                    it.also { result ->
                        result.registerMetrics()
                    }
                },
                onFailure = { t ->
                    logger.warn(t) { "Fikk ikke scannet fil: ${t.message}" }
                    throw t
                },
            ).any { it.infisert() }
    }
