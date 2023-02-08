package no.nav.dagpenger.mellomlagring.lagring

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.GoogleCloudStorageTestcontainer
import no.nav.dagpenger.mellomlagring.GoogleCloudStorageTestcontainer.IMAGE
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class S3StoreTest {
    companion object {
        private const val FIXED_HOST_PORT = 44443
    }

    private val gcsFixedHost by lazy {
        GoogleCloudStorageTestcontainer.createAndStart(FIXED_HOST_PORT)
    }

    private val gcs by lazy {
        GenericContainer<Nothing>(IMAGE)
            .also { container ->
                container.withExposedPorts(4443)
                container.withCreateContainerCmdModifier { cmd ->
                    cmd.withEntrypoint(
                        "/bin/fake-gcs-server",
                        "-backend",
                        "memory",
                        "-scheme",
                        "http"
                    )
                }
                container.setWaitStrategy(HostPortWaitStrategy())
                container.start()
            }
    }

    private val TODAY = ZonedDateTime.now()

    @Test
    fun `Exception hvis ikke bucket finnes`() {
        require(gcs.isRunning) { "Container is not running" }
        shouldThrow<IllegalStateException> {
            S3Store(
                gcpStorage = Config.localStorage(
                    storageUrl = "http://${gcs.host}:${gcs.firstMappedPort}",
                    createBucket = false
                ),
            )
        }
    }

    @Test
    fun `happy path lagre,listing, henting og sletting av filer`() {
        require(gcsFixedHost.isRunning) { "Container is not running" }

        val store = S3Store(
            gcpStorage = Config.localStorage(
                storageUrl = "http://${gcsFixedHost.host}:$FIXED_HOST_PORT",
                createBucket = true
            ),
        )

        store.lagre(
            Klump(
                innhold = "hubba".toByteArray(),
                klumpInfo = KlumpInfo(
                    objektNavn = "urn:vedlegg:id/hubba",
                    originalFilnavn = "hubba",
                    storrelse = 10,
                    eier = "hubba eier",
                    tidspunkt = TODAY
                )
            )
        ).isSuccess shouldBe true

        store.lagre(
            Klump(
                innhold = "bubba".toByteArray(),
                klumpInfo = KlumpInfo(
                    objektNavn = "urn:vedlegg:id/bubba",
                    originalFilnavn = "bubba",
                    storrelse = 0,
                    eier = null,
                    tidspunkt = TODAY
                )
            )
        ).isSuccess shouldBe true

        val orThrow = store.listKlumpInfo("urn:vedlegg:id").getOrThrow()
        orThrow shouldContainExactlyInAnyOrder listOf(
            KlumpInfo(objektNavn = "urn:vedlegg:id/bubba", originalFilnavn = "bubba", storrelse = 0, eier = null, tidspunkt = TODAY),
            KlumpInfo(objektNavn = "urn:vedlegg:id/hubba", originalFilnavn = "hubba", storrelse = 10, eier = "hubba eier", tidspunkt = TODAY),
        )

        store.listKlumpInfo("urn:vedlegg").getOrThrow().let { it.size shouldBe 2 }

        store.hent("urn:vedlegg:id/hubba").getOrNull().also {
            it shouldNotBe null
            it?.let { klump ->
                klump.klumpInfo.objektNavn shouldBe "urn:vedlegg:id/hubba"
                klump.klumpInfo.originalFilnavn shouldBe "hubba"
                klump.klumpInfo.eier shouldBe "hubba eier"
                klump.klumpInfo.storrelse shouldBe 10
                String(klump.innhold) shouldBe "hubba"
                klump.klumpInfo.tidspunkt shouldBe TODAY
            }
        }

        store.slett("urn:vedlegg:id/hubba").getOrThrow() shouldBe true
        store.hent("urn:vedlegg:id/hubba").getOrNull() shouldBe null
        store.hentKlumpInfo("urn:vedlegg:id/hubba").getOrNull() shouldBe null
    }
}
