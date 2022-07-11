package no.nav.dagpenger.mellomlagring.pdf

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import de.slub.urn.URN
import mu.KotlinLogging

val logg = KotlinLogging.logger("tjenestekall")
internal object BundleRequestDeserializer : JsonDeserializer<BundleRequest>() {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BundleRequest {
        return kotlin.runCatching {
            p.readValueAsTree<JsonNode>().let { node ->
                logg.info { "jsonparser node: ${node.toPrettyString()}" }
                BundleRequest(node.soknadId(), node.bundleNavn(), node.urns())
            }
        }.fold(
            onSuccess = { it },
            onFailure = { t ->
                throw IllegalArgumentException(t) }
        )
    }

    private fun JsonNode.soknadId() = this.get("soknadId").asText()
    private fun JsonNode.bundleNavn() = this.get("bundleNavn").asText()
    private fun JsonNode.urns(): Set<URN> = this.get("filer").map { urnNode ->
        URN.rfc8141().parse(urnNode.get("urn").asText())
    }.toSet()
}
