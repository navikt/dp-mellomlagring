package no.nav.dagpenger.mellomlagring.pdf

import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.lagring.Klump
import no.nav.dagpenger.mellomlagring.lagring.KlumpInfo
import no.nav.dagpenger.mellomlagring.monitoring.Metrics
import no.nav.dagpenger.mellomlagring.pdf.ImageProcessor.tilPdf
import no.nav.dagpenger.mellomlagring.vedlegg.Mediator
import no.nav.dagpenger.mellomlagring.vedlegg.NotFoundException
import no.nav.dagpenger.mellomlagring.vedlegg.VedleggUrn

private val sikkerlogg = KotlinLogging.logger("tjenestekall")
private val logger = KotlinLogging.logger { }

internal class BundleMediator(private val mediator: Mediator) {
    suspend fun bundle(request: BundleRequest, eier: String): KlumpInfo {
        Metrics.bundlerRequestCounter.inc()
        return kotlin.runCatching {
            val pdf: ByteArray = request.filer
                .map { hent(VedleggUrn(it.namespaceSpecificString().toString()), eier) }
                .map { it.getOrThrow().innhold }
                .map { it.tilPdf() }
                .reduce(ImageProcessor::mergePdf)

            mediator.lagre(
                soknadsId = request.soknadId,
                filnavn = request.bundleNavn,
                filinnhold = pdf,
                eier = eier,
                filContentType = "application/pdf"
            )
        }.onSuccess {
            logger.info { "Bundlet ${request.filer} -> ${it.objektNavn}" }
        }.onFailure {
            logger.warn(it) { "Feilet bundling av ${request.filer} med bundlenavn ${request.bundleNavn}. Se sikker logg for eier" }
            sikkerlogg.error { "Feilet bundling av ${request.filer} med bundlenavn ${request.bundleNavn} og eier $eier" }
            Metrics.bundlerErrorTypesCounter.labels(it.javaClass.simpleName).inc()
        }.getOrThrow()
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
