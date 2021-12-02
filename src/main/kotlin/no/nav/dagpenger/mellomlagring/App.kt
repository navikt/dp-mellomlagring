package no.nav.dagpenger.mellomlagring

import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.dagpenger.mellomlagring.api.health
import no.nav.dagpenger.mellomlagring.api.store
import no.nav.dagpenger.mellomlagring.lagring.S3Store

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(CallLogging)

        health()
        store(S3Store(Config.storage))
    }.start(wait = true)
}
