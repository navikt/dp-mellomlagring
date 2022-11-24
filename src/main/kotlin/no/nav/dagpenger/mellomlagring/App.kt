package no.nav.dagpenger.mellomlagring

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.logging.toLogString
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.Config.Crypto
import no.nav.dagpenger.mellomlagring.api.HttpProblem
import no.nav.dagpenger.mellomlagring.api.health
import no.nav.dagpenger.mellomlagring.auth.jwt
import no.nav.dagpenger.mellomlagring.av.clamAv
import no.nav.dagpenger.mellomlagring.lagring.S3Store
import no.nav.dagpenger.mellomlagring.monitoring.metrics
import no.nav.dagpenger.mellomlagring.pdf.BundleMediator
import no.nav.dagpenger.mellomlagring.pdf.pdfApi
import no.nav.dagpenger.mellomlagring.vedlegg.AntiVirusValidering
import no.nav.dagpenger.mellomlagring.vedlegg.FiltypeValidering
import no.nav.dagpenger.mellomlagring.vedlegg.MediatorImpl
import no.nav.dagpenger.mellomlagring.vedlegg.NotFoundException
import no.nav.dagpenger.mellomlagring.vedlegg.NotOwnerException
import no.nav.dagpenger.mellomlagring.vedlegg.PdfValidering
import no.nav.dagpenger.mellomlagring.vedlegg.UgyldigFilInnhold
import no.nav.dagpenger.mellomlagring.vedlegg.vedleggApi
import org.slf4j.event.Level
import java.util.UUID

private val logger = KotlinLogging.logger { }

fun main() {
    val mediator = MediatorImpl(
        store = S3Store(),
        filValideringer = listOf(FiltypeValidering, PdfValidering, AntiVirusValidering(clamAv())),
        aead = Crypto.aead
    )
    embeddedServer(CIO, port = 8080) {
        ktorFeatures()
        health()
        vedleggApi(mediator)
        pdfApi(BundleMediator(mediator))
        metrics()
    }.start(wait = true)
}

internal fun Application.ktorFeatures() {
    install(CallLogging) {
        callIdMdc("callId")
        disableDefaultColors()
        filter {
            !it.request.path().startsWith("/internal")
        }
        this.level = Level.INFO
        mdc("x_søknadId") { call ->
            call.parameters["id"]
        }
    }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.isNotEmpty() }
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
            logger.error(cause) { "Kunne ikke håndtere API kall: ${call.request.toLogString()}" }

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
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
}
