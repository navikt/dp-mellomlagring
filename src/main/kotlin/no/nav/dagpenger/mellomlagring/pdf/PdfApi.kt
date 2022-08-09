package no.nav.dagpenger.mellomlagring.pdf

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import de.slub.urn.URN
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.auth.azureAdEier
import no.nav.dagpenger.mellomlagring.vedlegg.VedleggUrn

internal fun Application.pdfApi(mediator: BundleMediator) {
    routing {
        route("v1/mellomlagring/pdf") {
            authenticate(Config.AzureAd.name) {
                bundle(mediator)
            }
        }
    }
}

private fun Route.bundle(mediator: BundleMediator) {
    route("/bundle") {
        post {
            val request = call.receive<BundleRequest>()
            val klumpInfo = mediator.bundle(request, call.azureAdEier())
            call.respond(HttpStatusCode.Created, BundleResponse(klumpInfo.originalFilnavn, VedleggUrn(klumpInfo.objektNavn)))
        }
    }
}

private data class BundleResponse(val filnavn: String, val urn: VedleggUrn)

@JsonDeserialize(using = BundleRequestDeserializer::class)
internal data class BundleRequest(
    val soknadId: String,
    val bundleNavn: String,
    val filer: Set<URN>
)
