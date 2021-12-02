package no.nav.dagpenger.mellomlagring.lagring

import no.nav.dagpenger.mellomlagring.Config
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class StoreTest {
    private val gcs by lazy {
        GenericContainer<Nothing>(DockerImageName.parse("fsouza/fake-gcs-server"))
            .also { container ->
                container.withExposedPorts(4443)
                container.withCreateContainerCmdModifier { cmd ->
                    cmd.withEntrypoint("/bin/fake-gcs-server", "-data", "/data", "-scheme", "http")
                }

                container.start()
            }
    }

    @Test
    fun `Start mellomlager`() {
        val host = "http://${gcs.host}:${gcs.firstMappedPort}"
        val store = S3Store(Config.localStorage(host))
        store.lagre(Store.VedleggHolder("hubbabubba", "hubbabubba".toByteArray()))
    }
}
