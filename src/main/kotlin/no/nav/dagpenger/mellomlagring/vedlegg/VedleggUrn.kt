package no.nav.dagpenger.mellomlagring.vedlegg

import com.fasterxml.jackson.annotation.JsonIgnore
import de.slub.urn.URN

internal class VedleggUrn(@JsonIgnore val nss: String) {
    companion object {
        const val VEDLEGG_NAMESPACE_IDENTIFIER = "vedlegg"
    }

    val urn: String = "urn:$VEDLEGG_NAMESPACE_IDENTIFIER:$nss"

    init {
        kotlin.runCatching {
            URN.rfc8141().parse(urn)
        }.onFailure {
            throw IllegalArgumentException(it)
        }
    }

    override fun equals(other: Any?): Boolean {
        return other != null && other is VedleggUrn && this.urn == other.urn
    }

    override fun hashCode() = urn.hashCode()

    override fun toString() = urn
}
