package no.nav.dagpenger.mellomlagring.vedlegg

import no.nav.dagpenger.io.Detect.isJpeg
import no.nav.dagpenger.io.Detect.isPdf
import no.nav.dagpenger.io.Detect.isPng
import no.nav.dagpenger.mellomlagring.av.AntiVirus
import no.nav.dagpenger.pdf.InvalidPDFDocument
import no.nav.dagpenger.pdf.PDFDocument
import no.nav.dagpenger.pdf.ValidPDFDocument

internal class AntiVirusValidering(private val antiVirus: AntiVirus) : Mediator.FilValidering {
    override suspend fun valider(filnavn: String, filinnhold: ByteArray): FilValideringResultat {
        return when (antiVirus.infisert(filnavn, filinnhold)) {
            true -> FilValideringResultat.Ugyldig(filnavn, "Fil har virus")
            else -> FilValideringResultat.Gyldig(filnavn)
        }
    }
}

internal object FiltypeValidering : Mediator.FilValidering {
    private fun ByteArray.gyldigFilFormat() = this.isJpeg() || this.isPng() || this.isPdf()
    override suspend fun valider(filnavn: String, filinnhold: ByteArray): FilValideringResultat =
        when (filinnhold.gyldigFilFormat()) {
            true -> FilValideringResultat.Gyldig(filnavn)
            else -> FilValideringResultat.Ugyldig(filnavn, "Fil er ikke av type JPEG, PNG eller PDF")
        }
}

internal object PdfValidering : Mediator.FilValidering {

    override suspend fun valider(filnavn: String, filinnhold: ByteArray): FilValideringResultat {
        return if (!filinnhold.isPdf()) return FilValideringResultat.Gyldig(filnavn) else {
            when (val p = PDFDocument.load(filinnhold)) {
                is InvalidPDFDocument -> FilValideringResultat.Ugyldig(filnavn, p.message() ?: "Ukjent pdf feil")
                is ValidPDFDocument -> FilValideringResultat.Gyldig(filnavn)
            }
        }
    }
}
