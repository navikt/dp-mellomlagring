package no.nav.dagpenger.mellomlagring.vedlegg

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.lagring.Klump
import no.nav.dagpenger.mellomlagring.lagring.KlumpInfo
import no.nav.dagpenger.mellomlagring.lagring.StorageKey
import no.nav.dagpenger.mellomlagring.lagring.Store
import no.nav.dagpenger.mellomlagring.lagring.StoreException

internal interface Mediator {

    suspend fun lagre(soknadsId: String, filnavn: String, filinnhold: ByteArray): VedleggUrn
    suspend fun liste(soknadsId: String): List<VedleggUrn>
    suspend fun hent(vedleggUrn: VedleggUrn): Klump?
    suspend fun slett(vedleggUrn: VedleggUrn): Boolean
    fun interface FilValidering {
        suspend fun valider(filnavn: String, filinnhold: ByteArray): FilValideringResultat
    }
}

internal sealed class FilValideringResultat(open val filnavn: String) {
    data class Gyldig(override val filnavn: String) : FilValideringResultat(filnavn)
    data class Ugyldig(override val filnavn: String, val feilMelding: String) : FilValideringResultat(filnavn)
}

internal class NotOwnerException(msg: String) : Throwable(msg)
internal class UgyldigFilInnhold(filnavn: String, feilMeldinger: List<String>) : Throwable() {
    override val message: String = "$filnavn feilet følgende valideringer: ${feilMeldinger.joinToString(", ")}"
}

private val logger = KotlinLogging.logger { }

internal class MediatorImpl(
    private val store: Store,
    private val filValideringer: List<Mediator.FilValidering> = emptyList()
) : Mediator {

    override suspend fun lagre(
        soknadsId: String,
        filnavn: String,
        filinnhold: ByteArray
    ): VedleggUrn {
        valider(filnavn, filinnhold)
        val navn = createStoreKey(soknadsId = soknadsId, fileName = filnavn)
        return store.lagre(
            klump = Klump(
                innhold = filinnhold,
                klumpInfo = KlumpInfo(
                    navn = navn,
                    metadata = emptyMap()
                )
            )
        ).getOrThrow().let { VedleggUrn(navn) }
    }

    override suspend fun liste(soknadsId: String): List<VedleggUrn> {
        return store
            .listKlumpInfo(soknadsId)
            .getOrThrow()
            .map { VedleggUrn(it.navn) }
    }

    override suspend fun hent(vedleggUrn: VedleggUrn): Klump? {
        return vedleggUrn.nss.let { klumpNavn ->
            hvisFinnes(klumpNavn) {
                store.hent(klumpNavn).getOrThrow()
            }
        }
    }

    override suspend fun slett(vedleggUrn: VedleggUrn): Boolean {
        return vedleggUrn.nss.let { klumpnavn ->
            hvisFinnes(klumpnavn) {
                store.slett(klumpnavn).getOrThrow()
            } ?: false
        }
    }

    private suspend fun valider(filnavn: String, filinnhold: ByteArray) {
        coroutineScope {
            filValideringer
                .map { async { it.valider(filnavn, filinnhold) } }
                .awaitAll()
                .filterIsInstance<FilValideringResultat.Ugyldig>()
                .map { it.feilMelding }
                .takeIf { it.isNotEmpty() }
                ?.let {
                    logger.error { }
                    throw UgyldigFilInnhold(filnavn, it)
                }
        }
    }

    private fun createStoreKey(soknadsId: String, fileName: String): StorageKey {
        return "$soknadsId/$fileName"
    }

    private fun <T> Result<T>.getOrThrow(): T {
        return this.getOrElse {
            when (it) {
                is NotOwnerException -> throw it
                is UgyldigFilInnhold -> throw it
                else -> throw StoreException(it.message ?: "Ukjent feil")
            }
        }
    }

    private inline fun <T> hvisFinnes(klumpNavn: String, block: () -> T?): T? {
        return store.hentKlumpInfo(klumpNavn).getOrThrow()?.let { _ ->
            block.invoke()
        }
    }
}
