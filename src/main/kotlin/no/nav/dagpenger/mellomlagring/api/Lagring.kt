package no.nav.dagpenger.mellomlagring.api

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.jackson.jackson
import io.ktor.request.receiveMultipart
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
                    val multipartData = call.receiveMultipart()
                    var fileDescription = ""
                    var fileName = ""
                    var fileBytes: ByteArray? = null

                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                fileDescription = part.value
                            }
                            is PartData.FileItem -> {
                                fileName = part.originalFileName as String
                                fileBytes = part.streamProvider().readBytes()
                            }
                            is PartData.BinaryItem -> TODO()
                        }
                        part.dispose()
                    }
                    fileBytes?.let {
                        store.lagre(Store.VedleggHolder(soknadsId, it)) // todo
                        call.respond(HttpStatusCode.Created)
                    }
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
