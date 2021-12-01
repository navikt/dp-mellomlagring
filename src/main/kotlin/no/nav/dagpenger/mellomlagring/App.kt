package no.nav.dagpenger.mellomlagring

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.dagpenger.mellomlagring.api.health
import no.nav.dagpenger.mellomlagring.api.store
import no.nav.dagpenger.mellomlagring.lagring.Store
import no.nav.dagpenger.mellomlagring.lagring.VedleggMetadata

fun main() {
    embeddedServer(Netty, port = 8080) {

        health()
        store(object : Store {
            override fun lagre(vedleggHolder: Store.VedleggHolder) {
                TODO("Not yet implemented")
            }

            override fun hent(soknadsId: String): List<VedleggMetadata> {
                TODO("Not yet implemented")
            }
        })
    }.start(wait = true)
}
