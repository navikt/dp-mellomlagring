package no.nav.dagpenger.mellomlagring.lagring

interface Store {

    fun lagre(storageKey: StorageKey, storageValue: StorageValue)
    fun hent(storageKey: StorageKey): StorageValue
    fun list(keyPrefix: StorageKey): List<VedleggMetadata>

    class VedleggHolder(val soknadsId: String, val innhold: ByteArray)
}

internal typealias StorageKey = String
internal typealias StorageValue = ByteArray

data class VedleggMetadata(val filnavn: String)
