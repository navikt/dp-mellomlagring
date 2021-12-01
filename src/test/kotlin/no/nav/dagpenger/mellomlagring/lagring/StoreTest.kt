package no.nav.dagpenger.mellomlagring.lagring

import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class StoreTest {

    private val gcs by lazy {
        GenericContainer<Nothing>(DockerImageName.parse("fsouza/fake-gcs-server"))
            .also {
                it.withExposedPorts(4443)
                it.start()
            }
    }

    @Test
    fun `Start mellomlager`() {
        val host = "https://${gcs.host}:${gcs.firstMappedPort}"
        val store = S3Store(host)
        store.lagre(Store.VedleggHolder("søknad1"))
    }
}
