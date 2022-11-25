package no.nav.dagpenger.mellomlagring.monitoring

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.dagpenger.mellomlagring.monitoring.Metrics.prometheusMeterRegistry

internal fun Application.metrics(
    registry: PrometheusMeterRegistry = prometheusMeterRegistry
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
