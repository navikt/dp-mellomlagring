package no.nav.dagpenger.mellomlagring.lagring

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.Config.Crypto
import no.nav.dagpenger.mellomlagring.GoogleCloudStorageTestcontainer
import no.nav.dagpenger.mellomlagring.vedlegg.NotFoundException
import no.nav.dagpenger.mellomlagring.vedlegg.NotOwnerException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.charset.Charset

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class KryptertStoreTest {
    companion object {
        val testFnr = "17659988198"
        val testFnr2 = "17659988196"
        private const val FIXED_HOST_PORT = 44447
    }

    private val gcsFixedHost by lazy {
        GoogleCloudStorageTestcontainer.createAndStart(FIXED_HOST_PORT)
    }

    @Test
    fun `happypath lagre, listing, henting og sletting`() {
        require(gcsFixedHost.isRunning) { "Container is not running" }
        val s3store = S3Store(
            gcpStorage = Config.localStorage(
                storageUrl = "http://${gcsFixedHost.host}:$FIXED_HOST_PORT",
                createBucket = true
            ),
        )
        val kryptertStore = KryptertStore(fnr = testFnr, store = s3store, aead = Crypto.aead)

        val lagretHubbaUrn = "urn:vedlegg:id/hubba"
        kryptertStore.lagre(
            Klump(
                innhold = "hubba".toByteArray(),
                klumpInfo = KlumpInfo(
                    navn = lagretHubbaUrn,
                    metadata = mapOf("meta" to "value")
                )
            )
        ).isSuccess shouldBe true

        kryptertStore.lagre(
            Klump(
                innhold = "hubba".toByteArray(),
                klumpInfo = KlumpInfo(
                    navn = "urn:vedlegg:id/bubba",
                    metadata = mapOf("meta" to "value")
                )
            )
        ).isSuccess shouldBe true

        s3store.hent(lagretHubbaUrn).getOrThrow().also {
            require(it != null)
            it.innhold.toString() shouldNotBe "hubba"
            Crypto.aead.decrypt(it.innhold, testFnr.toByteArray()).toString(Charset.forName("ISO-8859-1")) shouldBe "hubba"
        }
        kryptertStore.listKlumpInfo("urn:vedlegg:id").getOrThrow().size shouldBe 2

        kryptertStore.hent(lagretHubbaUrn).getOrNull().also {
            it shouldNotBe null
            it?.let { klump ->
                klump.klumpInfo.navn shouldBe lagretHubbaUrn
                String(klump.innhold) shouldBe "hubba"
            }
        }

        kryptertStore.hentKlumpInfo(lagretHubbaUrn).getOrThrow().also {
            it shouldNotBe null
            it?.let { klumpInfo ->
                klumpInfo.navn shouldBe lagretHubbaUrn
            }
        }

        kryptertStore.slett(lagretHubbaUrn).getOrThrow() shouldBe true
        kryptertStore.hent(lagretHubbaUrn).getOrNull() shouldBe null
        kryptertStore.hentKlumpInfo(lagretHubbaUrn).getOrNull() shouldBe null
    }

    @Test
    fun `Har ikke tilgang til ressurser andre eier`() {
        require(gcsFixedHost.isRunning) { "Container is not running" }
        val s3store = S3Store(
            gcpStorage = Config.localStorage(
                storageUrl = "http://${gcsFixedHost.host}:$FIXED_HOST_PORT",
                createBucket = true
            ),
        )
        KryptertStore(fnr = testFnr, store = s3store, aead = Crypto.aead).also {
            require(
                it.lagre(
                    Klump(
                        innhold = "hubba".toByteArray(),
                        klumpInfo = KlumpInfo(
                            navn = "urn:vedlegg:id/hubba",
                            metadata = mapOf("meta" to "value")
                        )
                    )
                ).isSuccess
            )
        }

        val annenEierStore = KryptertStore(fnr = testFnr2, store = s3store, aead = Crypto.aead)
        annenEierStore.hent("urn:vedlegg:id/hubba").also {
            it.exceptionOrNull() should beInstanceOf<NotOwnerException>()
        }
        annenEierStore.hentKlumpInfo("urn:vedlegg:id/hubba").also {
            it.exceptionOrNull() should beInstanceOf<NotOwnerException>()
        }
        annenEierStore.slett("urn:vedlegg:id/hubba").also {
            it.exceptionOrNull() should beInstanceOf<NotOwnerException>()
        }
        annenEierStore.hent("urn:vedlegg:id/hubba").also {
            it.exceptionOrNull() should beInstanceOf<NotOwnerException>()
        }
        annenEierStore.listKlumpInfo("urn:vedlegg:id/hubba").getOrThrow().size shouldBe 0

        annenEierStore.hent("urn:vedlegg:id/hubbara").also {
            it.exceptionOrNull() shouldBe beInstanceOf<NotFoundException>()
        }
    }
}
