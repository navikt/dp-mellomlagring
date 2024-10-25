package no.nav.dagpenger.mellomlagring.monitoring

import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.model.registry.PrometheusRegistry

object Metrics {
    internal val prometheusMeterRegistry =
        PrometheusMeterRegistry(
            PrometheusConfig.DEFAULT,
            PrometheusRegistry.defaultRegistry,
            Clock.SYSTEM,
        )

    val namespace = "dp_mellomlagring"

    val bundlerErrorTypesCounter =
        Counter
            .builder()
            .name("${namespace}_pdf_bundle_error_counter")
            .help("Teller feil på PDF bundling")
            .labelNames("exception_name")
            .register()

    val antivirusResultCounter =
        Counter
            .builder()
            .name("${namespace}_antivirus_result_count")
            .help("Teller antivirus results")
            .labelNames("antivirus_result")
            .register()

    val bundlerRequestCounter =
        Counter
            .builder()
            .name("${namespace}_pdf_bundle_request_counter")
            .help("Teller antall pdf bundle")
            .register()
}
