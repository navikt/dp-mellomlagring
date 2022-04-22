package no.nav.dagpenger.mellomlagring.api

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.health() {
    routing {
        route("internal") {
            get("isalive") {
                call.respondText("alive")
            }

            get("isready") {
                call.respondText("ready")
            }
        }
    }
}
