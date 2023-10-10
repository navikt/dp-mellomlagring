package no.nav.dagpenger.mellomlagring.vedlegg

import com.fasterxml.jackson.annotation.JsonIgnore
import de.slub.urn.URN

internal class VedleggUrn(
    @JsonIgnore val nss: String,
) {
    companion object {
        const val VEDLEGG_NAMESPACE_IDENTIFIER = "vedlegg"
    }

    val urn: String =
        kotlin.runCatching { URN.rfc8141().parse("urn:$VEDLEGG_NAMESPACE_IDENTIFIER:$nss") }
            .map { it.toString() }
            .onFailure { t -> throw IllegalArgumentException(t) }
            .getOrThrow()

    override fun equals(other: Any?): Boolean {
        return other != null && other is VedleggUrn && this.urn == other.urn
    }

    override fun hashCode() = urn.hashCode()

    override fun toString() = urn
}
