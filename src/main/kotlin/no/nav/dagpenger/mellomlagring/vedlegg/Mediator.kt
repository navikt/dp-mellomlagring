package no.nav.dagpenger.mellomlagring.vedlegg

import com.google.crypto.tink.Aead
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.dagpenger.mellomlagring.lagring.Klump
import no.nav.dagpenger.mellomlagring.lagring.KlumpInfo
import no.nav.dagpenger.mellomlagring.lagring.KryptertStore
import no.nav.dagpenger.mellomlagring.lagring.StorageKey
import no.nav.dagpenger.mellomlagring.lagring.Store
import no.nav.dagpenger.mellomlagring.lagring.StoreException
import no.nav.dagpenger.mellomlagring.monitoring.Metrics
import java.util.UUID

internal interface Mediator {
    suspend fun lagre(
        soknadsId: String,
        filnavn: String,
        filinnhold: ByteArray,
        filContentType: String,
        eier: String,
    ): KlumpInfo

    suspend fun liste(
        soknadsId: String,
        eier: String,
    ): List<KlumpInfo>

    suspend fun hent(
        vedleggUrn: VedleggUrn,
        eier: String,
    ): Klump?

    suspend fun slett(
        vedleggUrn: VedleggUrn,
        eier: String,
    ): Boolean

    fun interface FilValidering {
        suspend fun valider(
            filnavn: String,
            filinnhold: ByteArray,
        ): FilValideringResultat
    }
}

abstract class FilValideringResultat(
    open val filnavn: String,
) {
    data class Gyldig(
        override val filnavn: String,
    ) : FilValideringResultat(filnavn)

    data class Ugyldig(
        override val filnavn: String,
        val feilMelding: String,
        val feilType: FeilType,
    ) : FilValideringResultat(filnavn)
}

enum class FeilType {
    UNAVAILABLE,
    FILE_VIRUS,
    FILE_ILLEGAL_FORMAT,
    FILE_ENCRYPTED,
}

internal class NotOwnerException(
    msg: String,
) : Throwable(msg)

internal class UgyldigFilInnhold(
    filnavn: String,
    val feilMeldinger: Map<FeilType, String>,
) : Throwable() {
    override val message: String = "$filnavn feilet følgende valideringer: ${feilMeldinger.values.joinToString(", ")}"
}

internal class NotFoundException(
    ressursNøkkel: String,
) : Throwable() {
    override val message: String = "Fant ikke ressurse med nøkkel $ressursNøkkel"
}

private val logger = KotlinLogging.logger { }

internal class MediatorImpl(
    private val store: Store,
    private val aead: Aead,
    private val filValideringer: List<Mediator.FilValidering> = emptyList(),
    private val uuidGenerator: () -> UUID = UUID::randomUUID,
) : Mediator {
    private fun kryptertStore(eier: String) = KryptertStore(eier, store, aead)

    override suspend fun lagre(
        soknadsId: String,
        filnavn: String,
        filinnhold: ByteArray,
        filContentType: String,
        eier: String,
    ): KlumpInfo {
        Metrics.vedleggRequestCounter.inc()
        valider(filnavn, filinnhold)
        val klumpInfo =
            KlumpInfo(
                objektNavn = createStoreKey(soknadsId = soknadsId),
                originalFilnavn = filnavn,
                storrelse = filinnhold.size.toLong(),
                filContentType = filContentType,
                eier = eier,
            )
        return kryptertStore(eier)
            .lagre(
                klump =
                    Klump(
                        innhold = filinnhold,
                        klumpInfo = klumpInfo,
                    ),
            ).getOrThrow()
            .let { klumpInfo }
    }

    override suspend fun liste(
        soknadsId: String,
        eier: String,
    ): List<KlumpInfo> =
        kryptertStore(eier)
            .listKlumpInfo(soknadsId)
            .getOrThrow()

    override suspend fun hent(
        vedleggUrn: VedleggUrn,
        eier: String,
    ): Klump? =
        vedleggUrn.nss.let { klumpNavn ->
            kryptertStore(eier).let { s ->
                s.hvisFinnes(klumpNavn) {
                    s.hent(klumpNavn).getOrThrow()
                }
            }
        }

    override suspend fun slett(
        vedleggUrn: VedleggUrn,
        eier: String,
    ): Boolean =
        vedleggUrn.nss.let { klumpnavn ->
            kryptertStore(eier).let { s ->
                s.hvisFinnes(klumpnavn) {
                    s.slett(klumpnavn).getOrThrow()
                } ?: false
            }
        }

    private suspend fun valider(
        filnavn: String,
        filinnhold: ByteArray,
    ) {
        coroutineScope {
            filValideringer
                .map { async { it.valider(filnavn, filinnhold) } }
                .awaitAll()
                .filterIsInstance<FilValideringResultat.Ugyldig>()
                .associate { it.feilType to it.feilMelding }
                .takeIf { it.isNotEmpty() }
                ?.let {
                    throw UgyldigFilInnhold(filnavn, it).also { feil ->
                        Metrics.vedleggErrorTypesCounter.labelValues(feil.javaClass.simpleName).inc()
                        logger.warn { "Filvaliderings feil: ${feil.message}" }
                    }
                }
        }
    }

    private fun createStoreKey(soknadsId: String): StorageKey = "$soknadsId/${uuidGenerator()}"

    private fun <T> Result<T>.getOrThrow(): T =
        this.getOrElse {
            when (it) {
                is NotOwnerException -> throw it
                is UgyldigFilInnhold -> throw it
                is NotFoundException -> throw it
                else -> throw StoreException(it.message ?: "Ukjent feil")
            }
        }

    private inline fun <T> Store.hvisFinnes(
        klumpNavn: String,
        block: () -> T?,
    ): T? =
        this.hentKlumpInfo(klumpNavn).getOrThrow()?.let { _ ->
            block.invoke()
        }
}
