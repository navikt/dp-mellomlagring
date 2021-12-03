package no.nav.dagpenger.mellomlagring

import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.dagpenger.mellomlagring.api.health
import no.nav.dagpenger.mellomlagring.api.vedleggApi
import no.nav.dagpenger.mellomlagring.lagring.S3Store
import no.nav.dagpenger.mellomlagring.lagring.VedleggService

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(CallLogging)

        health()
        vedleggApi(VedleggService(S3Store(Config.storage)))
    }.start(wait = true)
}
