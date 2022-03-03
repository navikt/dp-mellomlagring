package no.nav.dagpenger.mellomlagring.monitoring

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

internal fun Application.metrics(
    registry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
) {
    install(MicrometerMetrics) {
        this.registry = registry
    }

    routing {
        get("/internal/metrics") {
            call.respond(registry.scrape())
        }
    }
}
