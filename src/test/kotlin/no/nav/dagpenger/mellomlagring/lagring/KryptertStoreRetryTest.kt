package no.nav.dagpenger.mellomlagring.lagring

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.dagpenger.mellomlagring.Config.Crypto
import no.nav.dagpenger.mellomlagring.lagring.KryptertStore.Companion.MAKS_NYE_FORSØK
import no.nav.dagpenger.mellomlagring.vedlegg.NotOwnerException
import org.junit.jupiter.api.Test

internal class KryptertStoreRetryTest {
    private val fnr = "17659988198"
    private val annetFnr = "17659988196"
    private val nøkkel = "soknad/fil"

    private class KodetMetadataStore(
        private var antallKodedeSvar: Int,
    ) : Store {
        var lagret: Klump? = null
        var antallMetadataKall = 0
        var antallInnholdKall = 0

        private fun klumpInfo(): KlumpInfo? =
            lagret?.klumpInfo?.let {
                antallMetadataKall++
                if (antallKodedeSvar-- > 0) it.copy(eier = "=?UTF-8?Q?kodet?=") else it
            }

        override fun hent(storageKey: StorageKey): Result<Klump?> = Result.success(lagret.also { antallInnholdKall++ })

        override fun lagre(klump: Klump): Result<Int> {
            lagret = klump
            return Result.success(201)
        }

        override fun slett(storageKey: StorageKey): Result<Boolean> = Result.success(true)

        override fun hentKlumpInfo(storageKey: StorageKey): Result<KlumpInfo?> = Result.success(klumpInfo())

        override fun listKlumpInfo(keyPrefix: StorageKey): Result<List<KlumpInfo>> = Result.success(listOfNotNull(klumpInfo()))
    }

    private fun lagreFil(store: Store) {
        KryptertStore(fnr, store, Crypto.aead)
            .lagre(
                Klump(
                    innhold = "innhold".toByteArray(),
                    klumpInfo = KlumpInfo(objektNavn = nøkkel, originalFilnavn = "fil", storrelse = 7, eier = fnr),
                ),
            ).getOrThrow()
    }

    @Test
    fun `leser metadata på nytt når eier er kodet og lykkes når rå verdi kommer`() {
        val store = KodetMetadataStore(antallKodedeSvar = 3).also { lagreFil(it) }

        KryptertStore(fnr, store, Crypto.aead)
            .hent(nøkkel)
            .getOrThrow()!!
            .innhold
            .decodeToString() shouldBe "innhold"

        store.antallMetadataKall shouldBe 4
        store.antallInnholdKall shouldBe 1
    }

    @Test
    fun `gir opp etter maks antall nye forsøk og gir NotOwner`() {
        val store = KodetMetadataStore(antallKodedeSvar = 100).also { lagreFil(it) }

        KryptertStore(fnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()

        store.antallMetadataKall shouldBe 1 + MAKS_NYE_FORSØK
        store.antallInnholdKall shouldBe 0
    }

    @Test
    fun `hentKlumpInfo og slett prøver også på nytt`() {
        val store = KodetMetadataStore(antallKodedeSvar = 2).also { lagreFil(it) }
        KryptertStore(fnr, store, Crypto.aead).hentKlumpInfo(nøkkel).isSuccess shouldBe true

        val store2 = KodetMetadataStore(antallKodedeSvar = 2).also { lagreFil(it) }
        KryptertStore(fnr, store2, Crypto.aead).slett(nøkkel).getOrThrow() shouldBe true
    }

    @Test
    fun `listing tar med fil med kodet eier etter nytt forsøk`() {
        val store = KodetMetadataStore(antallKodedeSvar = 2).also { lagreFil(it) }

        KryptertStore(fnr, store, Crypto.aead).listKlumpInfo("soknad").getOrThrow().size shouldBe 1
    }

    @Test
    fun `feil eier med rå verdi gir NotOwner uten nye forsøk`() {
        val store = KodetMetadataStore(antallKodedeSvar = 0).also { lagreFil(it) }

        KryptertStore(annetFnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()

        store.antallMetadataKall shouldBe 1
    }

    @Test
    fun `feil eier med kodet verdi får aldri tilgang`() {
        val store = KodetMetadataStore(antallKodedeSvar = 2).also { lagreFil(it) }

        KryptertStore(annetFnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
        store.antallInnholdKall shouldBe 0
    }
}
