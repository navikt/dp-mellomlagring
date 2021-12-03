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
import no.nav.dagpenger.mellomlagring.lagring.VedleggService

internal fun Application.vedleggApi(vedleggService: VedleggService) {
    install(ContentNegotiation) {
        jackson()
    }
    routing {
        route("v1/mellomlagring") {
            route("/{soknadsId}") {
                post {
                    val soknadsId =
                        call.parameters["soknadsId"] ?: throw IllegalArgumentException("Fant ikke soknadsId")
                    val multipartData = call.receiveMultipart()
                    var fileDescription = ""
                    var fileName: String = ""
                    var fileBytes: ByteArray? = null

                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                fileDescription = part.value
                            }
                            is PartData.FileItem -> {
                                fileName = part.originalFileName ?: throw IllegalArgumentException("Filnavn mangler")
                                fileBytes = part.streamProvider().readBytes()
                            }
                            is PartData.BinaryItem -> TODO()
                        }
                        part.dispose()
                    }
                    fileBytes?.let { filinnhold ->
                        vedleggService.lagre(soknadsId, fileName, filinnhold)
                        call.respond(HttpStatusCode.Created)
                    }
                }
                get {
                    val soknadsId =
                        call.parameters["soknadsId"] ?: throw IllegalArgumentException("Fant ikke soknadsId")
                    val vedlegg = vedleggService.hent(soknadsId)
                    call.respond(HttpStatusCode.OK, vedlegg)
                }
            }
        }
    }
}
