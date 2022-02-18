package no.nav.dagpenger.mellomlagring.api

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.jackson.jackson
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.response.respondText
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
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.lagring.VedleggService
import no.nav.security.token.support.ktor.tokenValidationSupport

private val logger = KotlinLogging.logger { }

internal fun Application.vedleggApi(vedleggService: VedleggService) {
    val fileUploadHandler = FileUploadHandler(vedleggService)

    install(ContentNegotiation) {
        jackson()
    }

    install(Authentication) {
        kotlin.runCatching {
            logger.debug { "installing auth feature" }
            tokenValidationSupport(
                name = "tokenx",
                config = Config.OAuth2IssuersConfig,
                additionalValidation = {
                    it.getClaims(Config.tokenxIssuerName)?.getStringClaim("pid") != null ||
                        it.getClaims(Config.tokenxIssuerName)?.getStringClaim("sub") != null
                }
            )
        }
            .onSuccess { logger.debug { "Finished installing auth feature" } }
            .onFailure { e -> logger.error(e) { "Failed installing auth feature" } }
    }

    routing {
        authenticate("tokenx") {
            route("v1/mellomlagring") {
                route("/{id}") {
                    post {
                        val id =
                            call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke id")
                        val multiPartData = call.receiveMultipart()
                        fileUploadHandler.handleFileupload(multiPartData, "", id)
                        call.respondText(
                            ContentType.Application.Json, HttpStatusCode.Created,
                            suspend {
                                """{"urn": "urn:vedlegg:$id"}"""
                            }
                        )
                    }
                    get {
                        val soknadsId =
                            call.parameters["id"] ?: throw IllegalArgumentException("Fant ikke soknadsId")
                        val vedlegg = vedleggService.hent(soknadsId)
                        call.respond(HttpStatusCode.OK, vedlegg)
                    }
                }
            }
        }
    }
}

private class FileUploadHandler(private val vedleggService: VedleggService) {
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
}
