package no.nav.dagpenger.mellomlagring.lagring

interface Store {
    fun lagre(storageKey: StorageKey, storageValue: StorageValue)
    fun hent(storageKey: StorageKey): List<VedleggMetadata>

    class VedleggHolder(val soknadsId: String, val innhold: ByteArray)
}

internal typealias StorageKey = String
internal typealias StorageValue = ByteArray

class VedleggMetadata(val soknadsId: String, val filnavn: String)
