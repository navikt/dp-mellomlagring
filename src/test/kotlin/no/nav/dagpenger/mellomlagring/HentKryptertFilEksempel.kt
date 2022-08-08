package no.nav.dagpenger.mellomlagring

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.StorageOptions
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.KmsEnvelopeAeadKeyManager
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient
import io.ktor.utils.io.core.toByteArray
import java.io.File
import java.nio.charset.Charset
import java.util.Optional

private object Kek {
    const val dev =
        "gcp-kms://projects/teamdagpenger-dev-885f/locations/europe-north1/keyRings/dp-mellomlagring/cryptoKeys/dp-mellomlagring"
    const val prod =
        "gcp-kms://projects/teamdagpenger-prod-9042/locations/europe-north1/keyRings/dp-mellomlagring/cryptoKeys/dp-mellomlagring"
}

/**
 *  Krever
 *   gcloud auth application-default login
 *   roles/cloudkms.cryptoKeyEncrypterDecrypter
 *   tilgang til bucket
 */
fun main() {
    AeadConfig.register()
    GcpKmsClient.register(Optional.of(Kek.dev), Optional.empty())

    val aead = KmsEnvelopeAeadKeyManager.createKeyTemplate(Kek.dev, KeyTemplates.get("AES128_GCM")).let {
        KeysetHandle.generateNew(it).getPrimitive(Aead::class.java)
    }.also { it.check() }

    val gcs = StorageOptions.getDefaultInstance().service

    val blob =
        gcs.get(BlobId.of("teamdagpenger-mellomlagring-dev", "39a0b3a8-bbf0-4673-9d8b-6747f956260f/Arbeidsforhold.pdf"))
    blob.getContent().let {
        println("Filstørellse: ${it.size}")
        aead.decrypt(it, "51818700273".toByteArray())
    }?.let {
        File("out.pdf").writeBytes(it)
    }
}

private fun Aead.check() {
    val charset = Charset.forName("ISO-8859-1")
    this.encrypt("hubba".toByteArray(charset), "hubba".toByteArray()).toString(charset).let {
        this.decrypt(it.toByteArray(charset), "hubba".toByteArray())
    }.let {
        assert("hubba" == it.toString(charset)) {
            println("Aead funker ei")
        }
    }
}
