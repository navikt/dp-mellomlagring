package no.nav.dagpenger.mellomlagring.lagring

import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import io.kotest.assertions.throwables.shouldThrow
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

    private val GenericContainer<Nothing>.instance: Storage
        get() {
            require(isRunning) { "Container is not running" }
            return Config.localStorage("http://$host:$firstMappedPort")
        }

    private fun withMockGCS(test: () -> Unit) {
        gcs.instance.create(BucketInfo.of(Config.bucketName))
        test.invoke()
    }

    @Test
    fun `Exception hvis ikke bucket finnes`() {
        shouldThrow<IllegalStateException> {
            S3Store(gcpStorage = gcs.instance)
        }
    }


    @Test
    fun `Start mellomlager`() = withMockGCS {
        val store = S3Store(gcs.instance)
        store.lagre(Store.VedleggHolder("hubbabubba", "hubbabubba".toByteArray()))
    }
}
