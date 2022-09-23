package no.nav.dagpenger.mellomlagring.pdf

import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.lagring.Klump
import no.nav.dagpenger.mellomlagring.lagring.KlumpInfo
import no.nav.dagpenger.mellomlagring.vedlegg.Mediator
import no.nav.dagpenger.mellomlagring.vedlegg.NotFoundException
import no.nav.dagpenger.mellomlagring.vedlegg.VedleggUrn

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class BundleMediator(private val mediator: Mediator) {
    suspend fun bundle(request: BundleRequest, eier: String): KlumpInfo {
        sikkerlogg.info { "Starter bundling av ${request.filer} med bundlenavn ${request.bundleNavn} og eier $eier" }
        val pdf: ByteArray = request.filer
            .map { hent(VedleggUrn(it.namespaceSpecificString().toString()), eier) }
            .map { it.getOrThrow().innhold }
            .reduce(ImageProcessor::convertAndMerge)

        sikkerlogg.info { "Pdfbundle ${request.bundleNavn} er klar" }
        return mediator.lagre(
            soknadsId = request.soknadId,
            filnavn = request.bundleNavn,
            filinnhold = pdf,
            eier = eier
        )
    }

    private suspend fun hent(urn: VedleggUrn, eier: String): Result<Klump> {
        return kotlin.runCatching {
            mediator.hent(urn, eier)
        }.mapCatching {
            it ?: throw NotFoundException("Fant ikke $urn")
        }.onFailure {
            sikkerlogg.error { "Henting av vedlegg $urn feilet for eier $eier" }
        }
    }
}
