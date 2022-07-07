package no.nav.dagpenger.mellomlagring

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.KmsEnvelopeAeadKeyManager
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import java.util.Optional

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
            "AZURE_APP_WELL_KNOWN_URL" to "http://localhost:4443",
            "AZURE_APP_CLIENT_ID" to "azureClientId",
            "TOKEN_X_WELL_KNOWN_URL" to "http://localhost:4443",
            "TOKEN_X_CLIENT_ID" to "tokenxClientId",
            "AZURE_APP_PRE_AUTHORIZED_APPS" to
                //language=JSON
                """ [ { "name": "EnApp", "clientId": "clientId-til-tillatt-app-123" } ]"""
        )
    )

    private val properties: Configuration
        get() =
            ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties

    object AzureAd {
        internal data class PreAuthorizedApp(val name: String, val clientId: String) {
            companion object {
                fun from(data: String): List<PreAuthorizedApp> = jacksonObjectMapper().readValue(data)
            }
        }

        const val name = "azureAd"
        val audience = properties[Key("AZURE_APP_CLIENT_ID", stringType)]
        val wellKnownUrl = properties[Key("AZURE_APP_WELL_KNOWN_URL", stringType)]
        val preAuthorizedApps = PreAuthorizedApp.from(properties[Key("AZURE_APP_PRE_AUTHORIZED_APPS", stringType)])
    }

    object TokenX {
        const val name = "tokenX"
        val audience = properties[Key("TOKEN_X_CLIENT_ID", stringType)]
        val wellKnownUrl = properties[Key("TOKEN_X_WELL_KNOWN_URL", stringType)]
    }

    val bucketName: String
        get() = properties[Key("DP_MELLOMLAGRING_BUCKETNAME", stringType)]

    val storage: Storage by lazy {
        when (env) {
            Env.LOCAL -> localStorage(properties[Key("DP_MELLOMLAGRING_STORAGE_URL", stringType)], true)
            else -> StorageOptions.getDefaultInstance().service
        }
    }

    object Crypto {
        val aead: Aead by lazy {
            AeadConfig.register()
            keysetHandle.getPrimitive(Aead::class.java)
        }
        private val keysetHandle: KeysetHandle by lazy {
            KeysetHandle.generateNew(keyTemplate)
        }

        private val kekUri by lazy {
            "gcp-kms://projects/${
            properties[
                Key(
                    "GCP_TEAM_PROJECT_ID",
                    stringType
                )
            ]
            }/locations/europe-north1/keyRings/dp-mellomlagring/cryptoKeys/dp-mellomlagring"
        }

        private val keyTemplate by lazy {
            when (env) {
                Env.LOCAL -> {
                    KeyTemplates.get("AES128_GCM")
                }
                else -> {
                    // ServiceAccount kommer fra en json fil på path GOOGLE_APPLICATION_CREDENTIALS i env
                    GcpKmsClient.register(Optional.of(kekUri), Optional.empty())
                    KmsEnvelopeAeadKeyManager.createKeyTemplate(kekUri, KeyTemplates.get("AES128_GCM"))
                }
            }
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
