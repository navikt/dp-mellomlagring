package no.nav.dagpenger.mellomlagring.api

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing

fun Application.health() {
    routing {
        route("internal") {
            get("isAlive") {
                call.respondText("isAlive")
            }

            get("isReady") {
                call.respondText("isReady")
            }
        }
    }
}
