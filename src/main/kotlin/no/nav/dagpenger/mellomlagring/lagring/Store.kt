package no.nav.dagpenger.mellomlagring.lagring

interface Store {
    fun lagre(hubba: Hubba)
    data class Hubba(val soknadsId: String)
}
