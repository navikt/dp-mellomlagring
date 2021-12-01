package no.nav.dagpenger.mellomlagring.api

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import no.nav.dagpenger.mellomlagring.lagring.Store

internal fun Application.store(store: Store) {
    routing {
        route("v1/mellomlagring/{soknadsId}") {
            post {
                val soknadsId = call.parameters["soknadsId"]!!
                store.lagre(Store.Hubba(soknadsId))
                call.respond(HttpStatusCode.Created)
            }
        }
    }
}
