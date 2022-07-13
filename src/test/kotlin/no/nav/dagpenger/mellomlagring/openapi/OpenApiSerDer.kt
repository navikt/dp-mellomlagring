package no.nav.dagpenger.mellomlagring.openapi

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

internal data class OpenApiSerDer(
    val openapi: String,
    val info: OpenApiInfo,
    val paths: Map<String, Map<String, OpenApiMethod>>
) {
    fun generateNewSpecFile(openApiSpec: OpenApiSpec): String =
        OpenApiSerDer(
            openapi,
            info = OpenApiInfo(title = info.title, version = info.version), // TODO: increment version
            paths = openApiSpec.paths.openapiserder()
        ).let {
            specObjectMapper.writeValueAsString(it)
        }

    companion object {
        internal val specObjectMapper =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun fromFile(openapiFilePath: String): OpenApiSerDer =
            specObjectMapper.readValue(
                File(openapiFilePath).readText(),
                OpenApiSerDer::class.java
            ) ?: throw IllegalArgumentException("File not found $openapiFilePath")
    }

    data class OpenApiInfo(val title: String, val version: String)
    data class OpenApiMethod(val description: String?, val parameters: List<OpenApiParameter>?)
    data class OpenApiParameter(val name: String?, val description: String?)
}

private fun List<Path>.openapiserder(): Map<String, Map<String, OpenApiSerDer.OpenApiMethod>> {
    return mutableMapOf<String, Map<String, OpenApiSerDer.OpenApiMethod>>().also { map ->
        forEach {
            map[it.value] = mapOf()
        }
    }
}
