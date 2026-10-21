package no.nav.dagpenger.mellomlagring.lagring

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.dagpenger.mellomlagring.Config.Crypto
import no.nav.dagpenger.mellomlagring.lagring.KryptertStore.Companion.MAKS_NYE_FORSØK
import no.nav.dagpenger.mellomlagring.vedlegg.NotOwnerException
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.util.Base64

internal class KryptertStoreRetryTest {
    private val fnr = "17659988198"
    private val annetFnr = "17659988196"
    private val nøkkel = "soknad/fil"
    private val kodetEier = "=?UTF-8?Q?kodet?="

    private class FakeGcs(
        private val gammeltObjekt: Boolean,
        private var antallKodedeSvar: Int,
    ) : Store {
        val lagret = linkedMapOf<String, Klump>()
        var antallMetadataKall = 0
        var antallListeKall = 0
        var antallInnholdKall = 0

        private fun metadata(klumpInfo: KlumpInfo): KlumpInfo {
            var info = if (gammeltObjekt) klumpInfo.copy(eierV2 = null) else klumpInfo
            if (antallKodedeSvar-- > 0) info = info.copy(eier = "=?UTF-8?Q?kodet?=")
            return info
        }

        override fun hent(storageKey: StorageKey): Result<Klump?> {
            antallInnholdKall++
            return Result.success(lagret[storageKey])
        }

        override fun lagre(klump: Klump): Result<Int> {
            lagret[klump.klumpInfo.objektNavn] = klump
            return Result.success(201)
        }

        override fun slett(storageKey: StorageKey): Result<Boolean> = Result.success(lagret.remove(storageKey) != null)

        override fun hentKlumpInfo(storageKey: StorageKey): Result<KlumpInfo?> {
            antallMetadataKall++
            return Result.success(lagret[storageKey]?.klumpInfo?.let { metadata(it) })
        }

        override fun listKlumpInfo(keyPrefix: StorageKey): Result<List<KlumpInfo>> {
            antallListeKall++
            return Result.success(lagret.values.filter { it.klumpInfo.objektNavn.startsWith(keyPrefix) }.map { metadata(it.klumpInfo) })
        }
    }

    private fun gcs(
        gammeltObjekt: Boolean = true,
        antallKodedeSvar: Int = 0,
        vararg nøkler: String = arrayOf(nøkkel),
    ) = FakeGcs(gammeltObjekt, antallKodedeSvar).also { store ->
        nøkler.forEach { lagreFil(store, it) }
    }

    private fun lagreFil(
        store: Store,
        objektNavn: String,
    ) {
        KryptertStore(fnr, store, Crypto.aead)
            .lagre(
                Klump(
                    innhold = "innhold".toByteArray(),
                    klumpInfo = KlumpInfo(objektNavn = objektNavn, originalFilnavn = "fil", storrelse = 7, eier = fnr),
                ),
            ).getOrThrow()
    }

    @Test
    fun `lagring skriver samme kryptert eier i eier og eier_v2`() {
        val store = gcs()
        val info = store.lagret.getValue(nøkkel).klumpInfo

        info.eierV2 shouldNotBe null
        Base64.getDecoder().decode(info.eierV2) shouldBe info.eier!!.toByteArray(Charset.forName("ISO-8859-1"))
        info.toMetadata()["eier_v2"] shouldBe info.eierV2
    }

    @Test
    fun `nytt objekt bruker eier_v2 og trenger ikke nye forsøk selv om eier er kodet`() {
        val store = gcs(gammeltObjekt = false, antallKodedeSvar = 100)

        KryptertStore(fnr, store, Crypto.aead)
            .hent(nøkkel)
            .getOrThrow()!!
            .innhold
            .decodeToString() shouldBe "innhold"
        store.antallMetadataKall shouldBe 1
    }

    @Test
    fun `nytt objekt med feil eier gir NotOwner`() {
        val store = gcs(gammeltObjekt = false)

        KryptertStore(annetFnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
        store.antallInnholdKall shouldBe 0
    }

    @Test
    fun `ugyldig base64 i eier_v2 gir NotOwner`() {
        val store = gcs(gammeltObjekt = false)
        store.lagret[nøkkel] = store.lagret.getValue(nøkkel).let { Klump(it.innhold, it.klumpInfo.copy(eierV2 = "ikke base64!")) }

        KryptertStore(fnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
    }

    @Test
    fun `gammelt objekt leser metadata på nytt når eier er kodet`() {
        val store = gcs(antallKodedeSvar = 3)

        KryptertStore(fnr, store, Crypto.aead)
            .hent(nøkkel)
            .getOrThrow()!!
            .innhold
            .decodeToString() shouldBe "innhold"
        store.antallMetadataKall shouldBe 4
        store.antallInnholdKall shouldBe 1
    }

    @Test
    fun `gammelt objekt gir opp etter maks antall nye forsøk`() {
        val store = gcs(antallKodedeSvar = 100)

        KryptertStore(fnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
        store.antallMetadataKall shouldBe 1 + MAKS_NYE_FORSØK
        store.antallInnholdKall shouldBe 0
    }

    @Test
    fun `hentKlumpInfo og slett prøver også på nytt`() {
        KryptertStore(fnr, gcs(antallKodedeSvar = 2), Crypto.aead).hentKlumpInfo(nøkkel).isSuccess shouldBe true
        KryptertStore(fnr, gcs(antallKodedeSvar = 2), Crypto.aead).slett(nøkkel).getOrThrow() shouldBe true
    }

    @Test
    fun `feil eier med rå verdi gir NotOwner uten nye forsøk`() {
        val store = gcs()

        KryptertStore(annetFnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
        store.antallMetadataKall shouldBe 1
    }

    @Test
    fun `feil eier med kodet verdi får aldri tilgang`() {
        val store = gcs(antallKodedeSvar = 2)

        KryptertStore(annetFnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
        store.antallInnholdKall shouldBe 0
    }

    @Test
    fun `listing lister på nytt til alle gamle objekter er verifisert`() {
        val store = gcs(antallKodedeSvar = 3, nøkler = arrayOf("soknad/a", "soknad/b"))

        KryptertStore(fnr, store, Crypto.aead)
            .listKlumpInfo("soknad")
            .getOrThrow()
            .map { it.objektNavn } shouldContainExactlyInAnyOrder listOf("soknad/a", "soknad/b")
        store.antallListeKall shouldBe 3
    }

    @Test
    fun `listing gir opp etter maks antall nye forsøk`() {
        val store = gcs(antallKodedeSvar = 1000)

        KryptertStore(fnr, store, Crypto.aead).listKlumpInfo("soknad").getOrThrow() shouldBe emptyList()
        store.antallListeKall shouldBe 1 + MAKS_NYE_FORSØK
    }

    @Test
    fun `listing med feil eier gir tom liste uten nye forsøk`() {
        val store = gcs()

        KryptertStore(annetFnr, store, Crypto.aead).listKlumpInfo("soknad").getOrThrow() shouldBe emptyList()
        store.antallListeKall shouldBe 1
    }

    @Test
    fun `listing av nye objekter trenger ikke nye forsøk`() {
        val store = gcs(gammeltObjekt = false, antallKodedeSvar = 100, nøkler = arrayOf("soknad/a", "soknad/b"))

        KryptertStore(fnr, store, Crypto.aead).listKlumpInfo("soknad").getOrThrow().size shouldBe 2
        store.antallListeKall shouldBe 1
    }
}
