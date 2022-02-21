package no.nav.dagpenger.mellomlagring.lagring

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.mellomlagring.Config
import org.junit.jupiter.api.Test
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer

class StoreTest {
    companion object {
        private const val FIXED_HOST_PORT = 44443
        private const val IMAGE = "fsouza/fake-gcs-server:1.33"
    }

    private val gcsFixedHost by lazy {
        // Because https://stackoverflow.com/questions/69337669/request-with-ipv4-from-python-to-gcs-emulator/70417427#70417427
        FixedHostPortGenericContainer<Nothing>(IMAGE)
            .also { container ->
                container.withFixedExposedPort(FIXED_HOST_PORT, 4443)
                container.withCreateContainerCmdModifier { cmd ->
                    cmd.withEntrypoint(
                        "/bin/fake-gcs-server",
                        "-external-url",
                        "http://localhost:$FIXED_HOST_PORT",
                        "-backend",
                        "memory",
                        "-scheme",
                        "http"
                    )
                }
                container.start()
            }
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
                container.start()
            }
    }

    @Test
    fun `Exception hvis ikke bucket finnes`() {
        require(gcs.isRunning) { "Container is not running" }
        shouldThrow<IllegalStateException> {
            S3Store(Config.localStorage("http://${gcs.host}:${gcs.firstMappedPort}", false))
        }
    }

    @Test
    fun `happy path lagre,listing og henting av filer`() {
        require(gcsFixedHost.isRunning) { "Container is not running" }
        val store = S3Store(Config.localStorage("http://${gcsFixedHost.host}:$FIXED_HOST_PORT", true))

        shouldNotThrowAny {
            store.lagre("id/hubba", "hubba".toByteArray(), "eier1")
            store.lagre("id/bubba", "bubba".toByteArray(), "eier2")
        }

        store.list("id").let {
            it.size shouldBe 2
            it shouldContainExactlyInAnyOrder listOf(VedleggMetadata("id/hubba", "eier1"), VedleggMetadata("id/bubba", "eier2"))
        }

        store.list("id/hubba").let {
            it.size shouldBe 1
            it shouldContainExactlyInAnyOrder listOf(VedleggMetadata("id/hubba", "eier1"))
        }

        shouldNotThrowAny {
            store.hent("id/hubba") shouldBe "hubba".toByteArray()
            store.hent("id/bubba") shouldBe "bubba".toByteArray()
        }
    }
}
