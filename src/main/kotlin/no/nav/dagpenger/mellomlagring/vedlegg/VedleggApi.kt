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
import java.time.LocalDateTime

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
        fun ApplicationCall.id(): String {
            return this.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
        }

        post {
            val multiPartData = call.receiveMultipart()
            val respond =
                fileUploadHandler.handleFileupload(multiPartData, call.id(), call.eierResolver())
                    .map(KlumpInfo::toResponse)
            call.respond(HttpStatusCode.Created, respond)
        }
        get {
            val vedlegg = mediator.liste(call.id(), call.eierResolver()).map { klumpinfo ->
                klumpinfo.toResponse()
            }
            call.respond(HttpStatusCode.OK, vedlegg)
        }

        route("/{subPath...}") {
            fun ApplicationCall.subPath(): String {
                return this.parameters.getAll("subPath")?.joinToString("/")
                    ?: throw IllegalArgumentException("Fant ikke subPath")
            }

            fun ApplicationCall.fullPath(): String {
                return listOf(this.id(), this.subPath()).joinToString("/")
            }

            post {
                val multiPartData = call.receiveMultipart()
                val respond =
                    fileUploadHandler.handleFileupload(multiPartData, call.fullPath(), call.eierResolver())
                        .map(KlumpInfo::toResponse)
                call.respond(HttpStatusCode.Created, respond)
            }

            get {
                mediator.hent(VedleggUrn(call.fullPath()), call.eierResolver())?.let {
                    call.respondOutputStream(ContentType.Application.OctetStream, HttpStatusCode.OK) {
                        withContext(Dispatchers.IO) {
                            this@respondOutputStream.write(it.innhold)
                        }
                    }
                }
            }
            delete {
                mediator.slett(VedleggUrn(call.fullPath()), call.eierResolver()).also {
                    when (it) {
                        true -> call.respond(HttpStatusCode.NoContent)
                        else -> call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}

private fun KlumpInfo.toResponse(): Response {
    val vedleggUrn = VedleggUrn(this.objektNavn)
    return Response(
        filnavn = this.originalFilnavn,
        urn = vedleggUrn.urn,
        filsti = vedleggUrn.nss,
        storrelse = this.storrelse,
        tidspunkt = this.tidspunkt
    )
}

private data class Response(
    val filnavn: String,
    val urn: String,
    val filsti: String,
    val storrelse: Long,
    val tidspunkt: LocalDateTime
)

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
