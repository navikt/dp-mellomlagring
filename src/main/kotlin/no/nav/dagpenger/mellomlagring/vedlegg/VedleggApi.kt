package no.nav.dagpenger.mellomlagring.vedlegg

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.auth.azureAdEier
import no.nav.dagpenger.mellomlagring.auth.oboEier
import no.nav.dagpenger.mellomlagring.lagring.KlumpInfo

private val logger = KotlinLogging.logger { }

internal fun Application.vedleggApi(mediator: Mediator) {
    val fileUploadHandler = FileUploadHandler(mediator)

    routing {
        route("v1/azuread") {
            authenticate(Config.AzureAd.name) {
                vedlegg(fileUploadHandler, mediator, ApplicationCall::azureAdEier)
            }
        }

        route("v1/obo") {
            authenticate(Config.TokenX.name) {
                vedlegg(fileUploadHandler, mediator, ApplicationCall::oboEier)
            }
        }
    }
}

internal fun Route.vedlegg(
    fileUploadHandler: FileUploadHandler,
    mediator: Mediator,
    eierResolver: ApplicationCall.() -> String
) {
    route("/mellomlagring/vedlegg/{id}") {
        post {
            val id =
                call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
            val multiPartData = call.receiveMultipart()
            val respond = fileUploadHandler.handleFileupload(multiPartData, id, call.eierResolver()).map { klumpInfo ->
                Respond(
                    filnavn = klumpInfo.originalFilnavn,
                    urn = VedleggUrn(klumpInfo.objektNavn).urn
                )
            }
            call.respond(HttpStatusCode.Created, respond)
        }
        get {
            val soknadsId =
                call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
            val vedlegg = mediator.liste(soknadsId, call.eierResolver()).map { klumpinfo ->
                Respond(
                    filnavn = klumpinfo.originalFilnavn,
                    urn = VedleggUrn(klumpinfo.objektNavn).urn
                )
            }
            call.respond(HttpStatusCode.OK, vedlegg)
        }
        route("/{filnavn}") {
            fun ApplicationCall.vedleggUrn(): VedleggUrn {
                val id = this.parameters["id"]
                val filnavn = this.parameters["filnavn"]
                return VedleggUrn("$id/$filnavn")
            }

            get {
                val vedleggUrn = call.vedleggUrn()
                mediator.hent(vedleggUrn, call.eierResolver())?.let {
                    call.respondOutputStream(ContentType.Application.OctetStream, HttpStatusCode.OK) {
                        withContext(Dispatchers.IO) {
                            this@respondOutputStream.write(it.innhold)
                        }
                    }
                }
            }
            delete {
                val vedleggUrn = call.vedleggUrn()
                mediator.slett(vedleggUrn, call.eierResolver()).also {
                    when (it) {
                        true -> call.respond(HttpStatusCode.NoContent)
                        else -> call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}

private data class Respond(val filnavn: String, val urn: String)

internal class FileUploadHandler(private val mediator: Mediator) {
    suspend fun handleFileupload(
        multiPartData: MultiPartData,
        soknadsId: String,
        eier: String
    ): List<KlumpInfo> {
        return coroutineScope {
            val jobs = mutableListOf<Deferred<KlumpInfo>>()
            multiPartData.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val fileName = part.originalFileName ?: throw IllegalArgumentException("Filnavn mangler")
                        jobs.add(
                            async(Dispatchers.IO) {
                                val bytes = part.streamProvider().readBytes()
                                mediator.lagre(soknadsId, fileName, bytes, eier)
                            }
                        )
                    }
                    is PartData.BinaryItem -> part.dispose().also {
                        logger.warn { "binary item not supported" }
                    }
                    is PartData.FormItem -> part.dispose().also {
                        logger.warn { "form item not supported" }
                    }
                    is PartData.BinaryChannelItem -> part.dispose().also {
                        logger.warn { "BinaryChannel item not supported" }
                    }
                }
            }
            jobs.awaitAll()
        }
    }
}
