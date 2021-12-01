package no.nav.dagpenger.mellomlagring

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.dagpenger.mellomlagring.api.health

fun main() {
    embeddedServer(Netty, port = 8080) {
        health()
    }.start(wait = true)
}
