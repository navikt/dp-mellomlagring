package no.nav.dagpenger.mellomlagring.monitoring

import io.prometheus.client.Counter

object Metrics {
    private val namespace = "dp_soknad"

    val bundlerErrorTypesCounter = Counter
        .build()
        .namespace(namespace)
        .name("pdf_bundle_error_counter")
        .help("Teller feil på PDF bundling")
        .labelNames("exception_name")
        .register()

    val bundlerRequestCounter = Counter
        .build()
        .namespace(namespace)
        .name("pdf_bundle_request_counter")
        .help("Teller antall pdf bundle")
        .register()
}
