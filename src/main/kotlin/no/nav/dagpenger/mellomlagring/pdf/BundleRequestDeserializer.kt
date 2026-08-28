package no.nav.dagpenger.mellomlagring.pdf

import de.slub.urn.URN
import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal object BundleRequestDeserializer : ValueDeserializer<BundleRequest>() {
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
                    sikkerlogg.error(t.cause) { "Kunne ikke deserialisere bundlerequest" }
                    throw IllegalArgumentException(t)
                },
            )

    private fun JsonNode.soknadId() = this["soknadId"].asString()

    private fun JsonNode.bundleNavn() = this.get("bundleNavn").asString()

    private fun JsonNode.urns(): Set<URN> =
        this["filer"]
            .toList()
            .map { urnNode ->
                URN.rfc8141().parse(urnNode.get("urn").asString())
            }.toSet()
}
