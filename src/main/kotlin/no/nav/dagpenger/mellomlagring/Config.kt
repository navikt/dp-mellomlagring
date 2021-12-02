package no.nav.dagpenger.mellomlagring

import com.google.cloud.NoCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions

internal object Config {
    internal enum class Env {
        LOCAL, CLOUD
    }

    private val env: Env = when (System.getenv().getOrDefault("NAIS_CLUSTER_NAME", "LOCAL")) {
        "LOCAL" -> Env.LOCAL
        else -> Env.CLOUD
    }

    const val bucketName: String = "teamdagpenger-mellomlagring-vedlegg"

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
