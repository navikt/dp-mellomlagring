package no.nav.dagpenger.mellomlagring.monitoring

import io.micrometer.core.instrument.Clock
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

object Metrics {
    internal val prometheusMeterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        CollectorRegistry.defaultRegistry,
        Clock.SYSTEM,
    )

    val namespace = "dp_mellomlagring"

    val bundlerErrorTypesCounter = Counter
        .build()
        .namespace(namespace)
        .name("pdf_bundle_error_counter")
        .help("Teller feil på PDF bundling")
        .labelNames("exception_name")
        .register()

    val antivirusResultCounter = Counter
        .build()
        .namespace(namespace)
        .name("antivirus_result_count")
        .help("Teller antivirus results")
        .labelNames("antivirus_result")
        .register()

    val bundlerRequestCounter = Counter
        .build()
        .namespace(namespace)
        .name("pdf_bundle_request_counter")
        .help("Teller antall pdf bundle")
        .register()
}
