package no.nav.dagpenger.mellomlagring.api

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import no.nav.dagpenger.mellomlagring.lagring.Store

internal fun Application.store(store: Store) {
    install(ContentNegotiation) {
        jackson()
    }
    routing {
        route("v1/mellomlagring") {
            route("/{soknadsId}") {
                post {
                    val soknadsId = call.parameters["soknadsId"] ?: throw IllegalArgumentException("Fant ikke soknadsId")
                    store.lagre(Store.VedleggHolder(soknadsId))
                    call.respond(HttpStatusCode.Created)
                }
                get {
                    val soknadsId = call.parameters["soknadsId"] ?: throw IllegalArgumentException("Fant ikke soknadsId")
                    val vedlegg = store.hent(soknadsId)
                    call.respond(HttpStatusCode.OK, vedlegg)
                }
            }
        }
    }
}
