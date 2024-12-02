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
    const val DEV =
        "gcp-kms://projects/teamdagpenger-dev-885f/locations/europe-north1/keyRings/dp-mellomlagring/cryptoKeys/dp-mellomlagring"
    const val PROD =
        "gcp-kms://projects/teamdagpenger-prod-9042/locations/europe-north1/keyRings/dp-mellomlagring/cryptoKeys/dp-mellomlagring"
}

/**
 *  Krever
 *   gcloud auth application-default login
 *   roles/cloudkms.cryptoKeyEncrypterDecrypter:
 *   gcloud projects add-iam-policy-binding teamdagpenger-dev-885f --member user:giao.the.cung@nav.no --role roles/cloudkms.cryptoKeyEncrypterDecrypter --condition="expression=request.time < timestamp('$(date -v '+1H' -u +'%Y-%m-%dT%H:%M:%SZ')'),title=temp_access"q
 *
 *   tilgang til bucket
 */
fun main() {
    AeadConfig.register()
    GcpKmsClient.register(Optional.of(Kek.PROD), Optional.empty())

    val aead =
        KmsEnvelopeAeadKeyManager.createKeyTemplate(Kek.PROD, KeyTemplates.get("AES128_GCM")).let {
            KeysetHandle.generateNew(it).getPrimitive(Aead::class.java)
        }.also { it.check() }

    val gcs = StorageOptions.getDefaultInstance().service

    val blob =
        gcs.get(BlobId.of("teamdagpenger-mellomlagring-prod", "oppgave/0193793f-4fe1-7e27-9e5c-735a951a06ee/40c38f27-f60b-4e7d-8664-1d91dd4ffb6c"))
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
