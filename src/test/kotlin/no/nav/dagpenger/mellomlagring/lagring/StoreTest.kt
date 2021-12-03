package no.nav.dagpenger.mellomlagring.lagring

import com.google.cloud.storage.Storage
import io.kotest.assertions.throwables.shouldThrow
import no.nav.dagpenger.mellomlagring.Config
import org.junit.jupiter.api.Disabled
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

    private fun GenericContainer<Nothing>.getInstance(createBucket: Boolean = true): Storage {
        require(isRunning) { "Container is not running" }
        return Config.localStorage("http://$host:$firstMappedPort", createBucket)
    }

    @Test
    fun `Exception hvis ikke bucket finnes`() {
        shouldThrow<IllegalStateException> {
            S3Store(gcs.getInstance(false))
        }
    }

    @Test
    @Disabled
    fun `Start mellomlager`() {
        val store = S3Store(gcs.getInstance())
        store.lagre("hubbabubba", "hubbabubba".toByteArray())
        store.hent("hubbabubba")
    }
}
