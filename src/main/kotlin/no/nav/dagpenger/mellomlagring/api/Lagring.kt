package no.nav.dagpenger.mellomlagring.api

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.lagring.VedleggService

private val logger = KotlinLogging.logger { }

internal fun Application.vedleggApi(vedleggService: VedleggService) {
    install(ContentNegotiation) {
        jackson()
    }

    suspend fun handleFileupload(multiPartData: MultiPartData, fnr: String, soknadsId: String) {
        coroutineScope {
            val jobs = mutableListOf<Deferred<Unit>>()
            multiPartData.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        jobs.add(
                            async(Dispatchers.IO) {
                                val fileName =
                                    part.originalFileName ?: throw IllegalArgumentException("Filnavn mangler")
                                part.streamProvider().use {
                                    // todo should we just use inputstream?
                                    vedleggService.lagre(soknadsId, fileName, it.readBytes())
                                }
                                part.dispose()
                            }
                        )
                    }
                    is PartData.BinaryItem -> part.dispose().also {
                        logger.warn { "binary item not supported" }
                    }
                    is PartData.FormItem -> part.dispose().also {
                        logger.warn { "form item not supported" }
                    }
                }
            }
            jobs.awaitAll()
        }
    }

    routing {
        route("v1/mellomlagring") {
            route("/{soknadsId}") {
                post {
                    val soknadsId =
                        call.parameters["soknadsId"] ?: throw IllegalArgumentException("Fant ikke soknadsId")
                    val multiPartData = call.receiveMultipart()
                    handleFileupload(multiPartData, "", soknadsId)
                    call.respond(HttpStatusCode.Created)
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
