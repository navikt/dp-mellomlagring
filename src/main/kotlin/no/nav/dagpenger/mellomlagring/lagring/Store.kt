package no.nav.dagpenger.mellomlagring.lagring

interface Store {
    fun lagre(vedleggHolder: VedleggHolder)
    fun hent(soknadsId: String): List<VedleggMetadata>

    class VedleggHolder(val soknadsId: String, val innhold: ByteArray)
}



internal typealias StorageKey = String
internal typealias StorageValue = ByteArray


class VedleggMetadata(val soknadsId: String, val filnavn: String)
