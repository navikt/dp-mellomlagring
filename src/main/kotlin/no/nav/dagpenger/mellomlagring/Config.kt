package no.nav.dagpenger.mellomlagring

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.dagpenger.mellomlagring.crypto.AESCrypto

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
            "DP_MELLOMLAGRING_BUCKETNAME" to "teamdagpenger-mellomlagring-local",
            "DP_MELLOMLAGRING_STORAGE_URL" to "http://localhost:4443",
            "DP_MELLOMLAGRING_CRYPTO_PASSPHRASE" to "a passphrase",
            "DP_MELLOMLAGRING_CRYPTO_SALT" to "rocksalt",
            "AZURE_APP_WELL_KNOWN_URL" to "http://localhost:4443",
            "AZURE_APP_CLIENT_ID" to "azureClientId",
            "TOKEN_X_WELL_KNOWN_URL" to "http://localhost:4443",
            "TOKEN_X_CLIENT_ID" to "tokenxClientId"
        )
    )

    private val properties: Configuration
        get() =
            ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties

    object crypto {
        val passPhrase = properties[Key("DP_MELLOMLAGRING_CRYPTO_PASSPHRASE", stringType)]
        val salt = properties[Key("DP_MELLOMLAGRING_CRYPTO_SALT", stringType)]
    }

    object AzureAd {
        const val name = "azureAd"
        val audience = properties[Key("AZURE_APP_CLIENT_ID", stringType)]
        val wellKnownUrl = properties[Key("AZURE_APP_WELL_KNOWN_URL", stringType)]
    }

    object TokenX {
        const val name = "tokenX"
        val audience = properties[Key("TOKEN_X_CLIENT_ID", stringType)]
        val wellKnownUrl = properties[Key("TOKEN_X_WELL_KNOWN_URL", stringType)]
    }

    fun crypto() = AESCrypto(
        passphrase = crypto.passPhrase,
        iv = crypto.salt
    )

    val bucketName: String
        get() = properties[Key("DP_MELLOMLAGRING_BUCKETNAME", stringType)]

    val storage: Storage by lazy {
        when (env) {
            Env.LOCAL -> localStorage(properties[Key("DP_MELLOMLAGRING_STORAGE_URL", stringType)], true)
            else -> StorageOptions.getDefaultInstance().service
        }
    }

    private val keysetHandle: KeysetHandle by lazy {
        when (env) {
            Env.LOCAL -> {
                KeysetHandle.generateNew(KeyTemplates.get("AES128_GCM"))
            }
            else -> {
                TODO("Integrate with key manager")
            }
        }
    }

    val aaed: Aead by lazy {
        AeadConfig.register()
        keysetHandle.getPrimitive<Aead>(Aead::class.java)
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
