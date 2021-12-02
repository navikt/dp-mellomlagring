package no.nav.dagpenger.mellomlagring.lagring

interface Store {
    fun lagre(vedleggHolder: VedleggHolder)
    fun hent(soknadsId: String): List<VedleggMetadata>

    class VedleggHolder(val soknadsId: String, val innhold: ByteArray)
}

class VedleggMetadata(val soknadsId: String, val filnavn: String)
