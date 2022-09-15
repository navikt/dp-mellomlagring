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
import no.nav.dagpenger.mellomlagring.auth.oboEier
import no.nav.dagpenger.mellomlagring.vedlegg.VedleggUrn
import java.time.ZonedDateTime

internal fun Application.pdfApi(mediator: BundleMediator) {
    routing {
        route("v1/obo/mellomlagring/pdf") {
            authenticate(Config.TokenX.name) {
                bundle(mediator)
            }
        }
    }
}

private fun Route.bundle(mediator: BundleMediator) {
    route("/bundle") {
        post {
            val request = call.receive<BundleRequest>()
            val klumpInfo = mediator.bundle(request, call.oboEier())
            val vedleggUrn = VedleggUrn(klumpInfo.objektNavn)
            call.respond(
                HttpStatusCode.Created,
                BundleResponse(
                    filnavn = klumpInfo.originalFilnavn,
                    urn = vedleggUrn.urn,
                    filsti = vedleggUrn.nss,
                    storrelse = klumpInfo.storrelse,
                    tidspunkt = klumpInfo.tidspunkt
                )
            )
        }
    }
}

private data class BundleResponse(
    val filnavn: String,
    val urn: String,
    val filsti: String,
    val storrelse: Long,
    val tidspunkt: ZonedDateTime
)

@JsonDeserialize(using = BundleRequestDeserializer::class)
internal data class BundleRequest(
    val soknadId: String,
    val bundleNavn: String,
    val filer: Set<URN>
)
