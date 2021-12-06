package no.nav.dagpenger.mellomlagring

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
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
        LOCAL, DEV, PROD
    }

    private val env: Env = when (System.getenv().getOrDefault("NAIS_CLUSTER_NAME", "LOCAL")) {
        "dev-gcp" -> Env.DEV
        "prod-gcp" -> Env.PROD
        else -> Env.LOCAL
    }

    private val defaultProperties = ConfigurationMap(
        mapOf(
            "DP_MELLOMLAGRING_BUCKETNAME" to "teamdagpenger-mellomlagring-vedlegg-local",
            "DP_MELLOMLAGRING_STORAGE_URL" to "http://localhost:50000"
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "DP_MELLOMLAGRING_BUCKETNAME" to "teamdagpenger-mellomlagring-vedlegg-dev",
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
                Env.DEV -> systemAndEnvProperties overriding devProperties overriding defaultProperties
                Env.PROD -> systemAndEnvProperties overriding prodProperties overriding defaultProperties
            }
        }

    object crypto {
        val passPhrase = properties[Key("DP_MELLOMLAGRING_CRYPTO_PASSPHRASE", stringType)]
        val salt = properties[Key("DP_MELLOMLAGRING_CRYPTO_SALT", stringType)]
    }

    val bucketName: String
        get() = properties[Key("DP_MELLOMLAGRING_BUCKETNAME", stringType)]

    val storage: Storage by lazy {
        when (env) {
            Env.LOCAL -> localStorage(properties[Key("DP_MELLOMLAGRING_STORAGE_URL", stringType)], true)
            else -> StorageOptions.getDefaultInstance().service
        }
    }

    internal fun localStorage(storageUrl: String, createBucket: Boolean) = StorageOptions.newBuilder()
        .setCredentials(NoCredentials.getInstance())
        .setHost(storageUrl) // From docker-compose
        .setProjectId("dagpenger")
        .build()
        .service.also {
            if (createBucket) it.create(BucketInfo.of(bucketName))
        }
}
