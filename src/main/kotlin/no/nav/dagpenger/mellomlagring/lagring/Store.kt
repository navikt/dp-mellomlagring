package no.nav.dagpenger.mellomlagring.lagring

interface Store {
    fun lagre(vedleggHolder: VedleggHolder)
    fun hent(soknadsId: String): List<VedleggMetadata>

    data class VedleggHolder(val soknadsId: String)
}

class VedleggMetadata(val soknadsId: String, val filnavn: String)
