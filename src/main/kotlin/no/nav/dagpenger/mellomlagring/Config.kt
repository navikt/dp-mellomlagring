package no.nav.dagpenger.mellomlagring

import com.google.cloud.NoCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

internal object Config {
    internal enum class Env {
        LOCAL, CLOUD
    }

    private val env: Env = when (System.getenv().getOrDefault("NAIS_CLUSTER_NAME", "LOCAL")) {
        "LOCAL" -> Env.LOCAL
        else -> Env.CLOUD
    }

    private val defaultProperties = ConfigurationMap(
        mapOf(
            "DP_MELLOMLAGRING_BUCKETNAME" to "teamdagpenger-mellomlagring-vedlegg-dev"
        )
    )
    private val prodProperties = ConfigurationMap(
        mapOf(
            "DP_MELLOMLAGRING_BUCKETNAME" to "teamdagpenger-mellomlagring-vedlegg-prod"
        )
    )

    private val properties: Configuration
        get() {
            val systemAndEnvProperties = ConfigurationProperties.systemProperties() overriding EnvironmentVariables()
            return when (env) {
                Env.LOCAL -> systemAndEnvProperties overriding defaultProperties
                Env.CLOUD -> systemAndEnvProperties overriding prodProperties overriding defaultProperties
            }
        }

    val bucketName: String
        get() = properties[Key("DP_MELLOMLAGRING_BUCKETNAME", stringType)]

    val storage: Storage = when (env) {
        Env.LOCAL -> localStorage()
        Env.CLOUD -> StorageOptions.getDefaultInstance().service
    }

    internal fun localStorage(host: String = "http://localhost:50000") = StorageOptions.newBuilder()
        .setCredentials(NoCredentials.getInstance())
        .setHost(host) // From docker-compose
        .setProjectId("dagpenger") // TODO
        .build()
        .service
}
