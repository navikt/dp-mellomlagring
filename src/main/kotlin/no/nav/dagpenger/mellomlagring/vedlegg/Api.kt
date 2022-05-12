package no.nav.dagpenger.mellomlagring.vedlegg

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.api.HttpProblem
import no.nav.dagpenger.mellomlagring.auth.jwt

private val logger = KotlinLogging.logger { }

internal fun Application.vedleggApi(mediator: Mediator) {
    val fileUploadHandler = FileUploadHandler(mediator)

    install(ContentNegotiation) {
        jackson()
    }

    install(Authentication) {
        jwt(Config.AzureAd.name, Config.AzureAd.wellKnownUrl) {
            withAudience(Config.AzureAd.audience)
        }

        jwt(Config.TokenX.name, Config.TokenX.wellKnownUrl) {
            withAudience(Config.TokenX.audience)
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Kunne ikke håndtere API kall" }

            when (cause) {
                is IllegalArgumentException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        HttpProblem(title = "Klient feil", status = 400, detail = cause.message)
                    )
                }
                is NotOwnerException -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        HttpProblem(title = "Ikke gyldig eier", status = 403, detail = cause.message)
                    )
                }
                is UgyldigFilInnhold -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        HttpProblem(title = "Fil er ugyldig", status = 400, detail = cause.message)
                    )
                }
                else -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        HttpProblem(title = "Feilet", detail = cause.message)
                    )
                }
            }
        }
    }

    routing {
        route("v1/azuread") {
            authenticate(Config.AzureAd.name) {
                vedlegg(fileUploadHandler, mediator)
            }
        }

        route("v1/obo") {
            authenticate(Config.TokenX.name) {
                vedlegg(fileUploadHandler, mediator)
            }
        }
    }
}

internal fun Route.vedlegg(fileUploadHandler: FileUploadHandler, mediator: Mediator) {
    route("/mellomlagring/vedlegg/{id}") {
        post {
            val id =
                call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
            val multiPartData = call.receiveMultipart()
            val urn = fileUploadHandler.handleFileupload(multiPartData, id)
            call.respond(HttpStatusCode.Created, urn)
        }
        get {
            val soknadsId =
                call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
            val vedlegg = mediator.liste(soknadsId)
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
                mediator.hent(vedleggUrn)?.let {
                    call.respondOutputStream(ContentType.Application.OctetStream, HttpStatusCode.OK) {
                        withContext(Dispatchers.IO) {
                            this@respondOutputStream.write(it.innhold)
                        }
                    }
                }
            }
            delete {
                val vedleggUrn = call.vedleggUrn()
                mediator.slett(vedleggUrn).also {
                    when (it) {
                        true -> call.respond(HttpStatusCode.NoContent)
                        else -> call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}

internal class FileUploadHandler(private val mediator: Mediator) {
    suspend fun handleFileupload(multiPartData: MultiPartData, soknadsId: String): VedleggUrn {
        return with(multiPartData.readAllParts().first { it is PartData.FileItem } as PartData.FileItem) {
            val fileName = this.originalFileName ?: throw IllegalArgumentException("Filnavn mangler")
            val bytes = this.streamProvider().use { it.readBytes() }
            this.dispose()
            mediator.lagre(soknadsId, fileName, bytes)
        }
    }
}
