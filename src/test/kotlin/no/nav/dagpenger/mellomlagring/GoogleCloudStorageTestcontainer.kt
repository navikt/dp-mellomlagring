package no.nav.dagpenger.mellomlagring

import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

object GoogleCloudStorageTestcontainer {
    const val IMAGE = "fsouza/fake-gcs-server:1.45.2"

    fun createAndStart(fixedPort: Int): FixedHostPortGenericContainer<Nothing> {
        // Because https://stackoverflow.com/questions/69337669/request-with-ipv4-from-python-to-gcs-emulator/70417427#70417427
        // we need to have a fixed port
        @Suppress("DEPRECATION")
        return FixedHostPortGenericContainer<Nothing>(IMAGE)
            .also { container ->
                container.withFixedExposedPort(fixedPort, 4443)
                container.withCreateContainerCmdModifier { cmd ->
                    cmd.withEntrypoint(
                        "/bin/fake-gcs-server",
                        "-external-url",
                        "http://localhost:$fixedPort",
                        "-backend",
                        "memory",
                        "-scheme",
                        "http",
                    )
                }
                container.setWaitStrategy(HostPortWaitStrategy())
                container.start()
            }
    }
}
