package no.nav.dagpenger.mellomlagring.vedlegg

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.api.HttpProblem
import no.nav.dagpenger.mellomlagring.auth.azureAdEier
import no.nav.dagpenger.mellomlagring.auth.jwt
import no.nav.dagpenger.mellomlagring.auth.oboEier

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
                is NotFoundException -> {
                    call.respond(
                        HttpStatusCode.NotFound,
                        HttpProblem(title = "Ressurs ikke funnet", status = 404, detail = cause.message)
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

internal fun Route.vedlegg(fileUploadHandler: FileUploadHandler, mediator: Mediator, eierResolver: ApplicationCall.() -> String) {
    val bundelHandler = BundleFileUploadHandler(mediator)

    route("/mellomlagring/bundle/{id}") {
        post {
            val id =
                call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
            val multiPartData = call.receiveMultipart()
            val respond = bundelHandler.handleFileupload(multiPartData, id, call.eierResolver()).let {
                Respond(
                    filnavn = it.first,
                    urn = it.second.urn
                )
            }
            call.respond(HttpStatusCode.Created, respond)
        }
    }

    route("/mellomlagring/vedlegg/{id}") {
        post {
            val id =
                call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
            val multiPartData = call.receiveMultipart()
            val respond = fileUploadHandler.handleFileupload(multiPartData, id, call.eierResolver()).map { e ->
                Respond(
                    filnavn = e.key,
                    urn = e.value.urn
                )
            }
            call.respond(HttpStatusCode.Created, respond)
        }
        get {
            val soknadsId =
                call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
            val vedlegg = mediator.liste(soknadsId, call.eierResolver())
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
    suspend fun handleFileupload(multiPartData: MultiPartData, soknadsId: String, eier: String): Map<String, VedleggUrn> {
        return coroutineScope {
            val jobs = mutableMapOf<String, Deferred<VedleggUrn>>()
            multiPartData.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val fileName = part.originalFileName ?: throw IllegalArgumentException("Filnavn mangler")
                        jobs[fileName] = async(Dispatchers.IO) {
                            val bytes = part.streamProvider().readBytes()
                            mediator.lagre(soknadsId, fileName, bytes, eier)
                        }
                    }
                    is PartData.BinaryItem -> part.dispose().also {
                        logger.warn { "binary item not supported" }
                    }
                    is PartData.FormItem -> part.dispose().also {
                        logger.warn { "form item not supported" }
                    }
                    is PartData.BinaryChannelItem -> part.dispose().also {
                        logger.warn { "BinaryChannel itme not supported" }
                    }
                }
            }
            jobs.mapValues { it.value.await() }
        }
    }
}
