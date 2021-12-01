package no.nav.dagpenger.mellomlagring

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.dagpenger.mellomlagring.api.health
import no.nav.dagpenger.mellomlagring.api.store
import no.nav.dagpenger.mellomlagring.lagring.Store

fun main() {
    embeddedServer(Netty, port = 8080) {
        health()
        store(object : Store {
            override fun lagre(hubba: Store.Hubba) {
                TODO("Not yet implemented")
            }
        })
    }.start(wait = true)
}
