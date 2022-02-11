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
import io.ktor.config.ApplicationConfig
import io.ktor.config.MapApplicationConfig

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
            "DP_MELLOMLAGRING_STORAGE_URL" to "http://localhost:4443",
            "DP_MELLOMLAGRING_CRYPTO_PASSPHRASE" to "a passphrase",
            "DP_MELLOMLAGRING_CRYPTO_SALT" to "rocksalt",
            "TOKEN_X_ACCEPTED_AUDIENCE" to "audience"
        )
    )

    private val properties: Configuration
        get() =
            ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties

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

    const val tokenxIssuerName = "tokenx"

    val OAuth2IssuersConfig: ApplicationConfig by lazy {
        MapApplicationConfig(
            "no.nav.security.jwt.expirythreshold" to "60",
            "no.nav.security.jwt.issuers.size" to "1", // to enable list config
            "no.nav.security.jwt.issuers.0.issuer_name" to tokenxIssuerName,
            "no.nav.security.jwt.issuers.0.discoveryurl" to properties[Key("TOKEN_X_WELL_KNOWN_URL", stringType)],
            "no.nav.security.jwt.issuers.0.cookie_name" to "selvbetjening-idtoken",
            "no.nav.security.jwt.issuers.0.accepted_audience" to properties[
                Key(
                    "TOKEN_X_ACCEPTED_AUDIENCE", stringType
                )
            ]
        )
    }
}
