package no.nav.dagpenger.mellomlagring.vedlegg

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.features.NotFoundException
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.jackson.jackson
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.response.respondOutputStream
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.api.HttpProblem
import no.nav.security.token.support.ktor.tokenValidationSupport

private val logger = KotlinLogging.logger { }

internal fun Application.vedleggApi(mediator: Mediator) {
    val fileUploadHandler = FileUploadHandler(mediator)

    install(ContentNegotiation) {
        jackson()
    }

    install(Authentication) {
        kotlin.runCatching {
            logger.debug { "installing auth feature" }
            tokenValidationSupport(
                name = Config.tokenxIssuerName,
                config = Config.OAuth2IssuerConfig,
                additionalValidation = {
                    it.getClaims(Config.tokenxIssuerName)?.getStringClaim("pid") != null
                }
            )

            tokenValidationSupport(
                name = Config.azureAdIssuerName,
                config = Config.OAuth2IssuerConfig,
            )
        }
            .onSuccess { logger.debug { "Finished installing auth feature" } }
            .onFailure { e -> logger.error(e) { "Failed installing auth feature" } }
    }

    install(StatusPages) {
        exception<Throwable> { cause ->
            logger.error(cause) { "Kunne ikke håndtere API kall" }
            call.respond(
                HttpStatusCode.InternalServerError,
                HttpProblem(title = "Feilet", detail = cause.message)
            )
        }
        exception<IllegalArgumentException> { cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                HttpProblem(title = "Klient feil", status = 400, detail = cause.message)
            )
        }
        exception<NotFoundException> { cause ->
            call.respond(
                HttpStatusCode.NotFound,
                HttpProblem(title = "Ikke funnet", status = 404, detail = cause.message)
            )
        }

        exception<NotOwnerException> { cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                HttpProblem(title = "Ikke gyldig eier", status = 403, detail = cause.message)
            )
        }

        exception<UgyldigFilInnhold> { cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                HttpProblem(title = "Fil er ugyldig", status = 400, detail = cause.message)
            )
        }
    }

    routing {
        authenticate(Config.tokenxIssuerName, Config.azureAdIssuerName) {
            route("v1/mellomlagring/vedlegg") {
                route("/{id}") {
                    post {
                        val id =
                            call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
                        val multiPartData = call.receiveMultipart()
                        val urnList = fileUploadHandler.handleFileupload(multiPartData, id)
                        call.respond(HttpStatusCode.Created, urnList)
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

                        get() {
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
        }
    }
}

private class FileUploadHandler(private val mediator: Mediator) {
    suspend fun handleFileupload(multiPartData: MultiPartData, soknadsId: String): List<VedleggUrn> {
        return coroutineScope {
            val jobs = mutableListOf<Deferred<VedleggUrn>>()
            multiPartData.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        jobs.add(
                            async(Dispatchers.IO) {
                                val fileName =
                                    part.originalFileName ?: throw IllegalArgumentException("Filnavn mangler")
                                val bytes = part.streamProvider().use { it.readBytes() }
                                part.dispose()
                                mediator.lagre(soknadsId, fileName, bytes)
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
}
