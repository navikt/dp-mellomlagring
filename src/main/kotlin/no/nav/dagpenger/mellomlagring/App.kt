package no.nav.dagpenger.mellomlagring

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.request.path
import no.nav.dagpenger.mellomlagring.api.health
import no.nav.dagpenger.mellomlagring.av.clamAv
import no.nav.dagpenger.mellomlagring.lagring.S3Store
import no.nav.dagpenger.mellomlagring.monitoring.metrics
import no.nav.dagpenger.mellomlagring.vedlegg.MediatorImpl
import no.nav.dagpenger.mellomlagring.vedlegg.VirusValidering
import no.nav.dagpenger.mellomlagring.vedlegg.vedleggApi
import org.slf4j.event.Level

fun main() {
    embeddedServer(CIO, port = 8080) {
        install(CallLogging) {
            disableDefaultColors()
            filter {
                !it.request.path().startsWith("/internal")
            }
            this.level = Level.DEBUG
        }
        health()
        vedleggApi(
            MediatorImpl(
                store = S3Store(),
                filValideringer = listOf(VirusValidering(clamAv()))
            )
        )
        metrics()
    }.start(wait = true)
}
