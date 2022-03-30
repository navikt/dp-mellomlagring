package no.nav.dagpenger.mellomlagring.vedlegg

import no.nav.dagpenger.mellomlagring.av.AntiVirus

internal class VirusValidering(private val antiVirus: AntiVirus) : Mediator.FilValidering {
    override suspend fun valider(filnavn: String, filinnhold: ByteArray): FilValideringResultat {
        return when (antiVirus.infisert(filnavn, filinnhold)) {
            true -> FilValideringResultat.Ugyldig(filnavn, "Fil har virus")
            else -> FilValideringResultat.Gyldig(filnavn)
        }
    }
}
