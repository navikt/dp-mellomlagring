package no.nav.dagpenger.mellomlagring.pdf

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import de.slub.urn.URN
import io.github.oshai.kotlinlogging.KotlinLogging

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal object BundleRequestDeserializer : JsonDeserializer<BundleRequest>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): BundleRequest =
        kotlin
            .runCatching {
                p.readValueAsTree<JsonNode>().let { node ->
                    BundleRequest(node.soknadId(), node.bundleNavn(), node.urns())
                }
            }.fold(
                onSuccess = { it },
                onFailure = { t ->
                    sikkerlogg.error("Kunne ikke deserialisere bundlerequest: " + t.cause)
                    throw IllegalArgumentException(t)
                },
            )

    private fun JsonNode.soknadId() = this["soknadId"].asText()

    private fun JsonNode.bundleNavn() = this.get("bundleNavn").asText()

    private fun JsonNode.urns(): Set<URN> =
        this
            .get("filer")
            .map { urnNode ->
                URN.rfc8141().parse(urnNode.get("urn").asText())
            }.toSet()
}
