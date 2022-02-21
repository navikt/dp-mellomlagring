package no.nav.dagpenger.mellomlagring.lagring

interface Store {

    fun hent(storageKey: StorageKey): StorageValue
    fun list(keyPrefix: StorageKey): List<VedleggMetadata>
    fun lagre(storageKey: StorageKey, storageValue: StorageValue, eier: String)
}

internal typealias StorageKey = String
internal typealias StorageValue = ByteArray

data class VedleggMetadata(val filnavn: String, val eier: String)
