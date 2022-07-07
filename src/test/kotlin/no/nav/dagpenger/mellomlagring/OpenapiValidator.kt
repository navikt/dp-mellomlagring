package no.nav.dagpenger.mellomlagring

import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.mockk.InternalPlatformDsl.toStr
import io.mockk.mockk
import no.nav.dagpenger.mellomlagring.vedlegg.vedleggApi
import no.nav.security.mock.oauth2.http.objectMapper
import java.io.File

class OpenapiValidator {
    fun validerOpenapi() {
        TestApplication.withMockAuthServerAndTestApplication({
            vedleggApi(mockk(relaxed = true))
        }) {
            this.application { ktorRoutes() }
        }
    }
}

fun main() {
    OpenapiValidator().validerOpenapi()
}

fun Application.ktorRoutes() {
    // set up omitted
    val root = plugin(Routing)
    val allRoutes = allRoutes(root)
    val allRoutesWithMethod =
        allRoutes.filter { it.selector is HttpMethodRouteSelector }
            .groupBy { it.parent }
    val currentSpec = Spec.fromJson()
    val newPaths = mutableListOf<Spec.Path>()
    allRoutesWithMethod.forEach { route ->
        Spec.Path.fromRoute(route)
        val methods = mutableListOf<String>()
        val path = route.key.toStr().split("/(authenticate azureAd)", "/(authenticate tokenX)").let { it.first() + it.last() + ":" }
        route.value.forEach { methods.add((it.selector as HttpMethodRouteSelector).method.value.lowercase()) }
        newPaths.add(Spec.Path(path, methods))
    }
    val newSpec = Spec(newPaths)

    println(currentSpec)
    println(newSpec)
}

fun allRoutes(root: Route): List<Route> {
    return listOf(root) + root.children.flatMap { allRoutes(it) }
}

private data class Spec(val paths: List<Path>) {
    companion object {
        fun fromJson(): Spec {
            val paths = mutableListOf<Path>()
            objectMapper.readTree(File(System.getProperty("user.dir") + "/doc/openapi.json")).let {
                it["paths"].fields().forEachRemaining {
                    val methods = mutableListOf<String>()
                    it.value.fields().forEachRemaining {
                        methods.add(it.key)
                    }
                    paths.add(Path(it.key, methods))
                }
            }
            return Spec(paths)
        }
    }
    fun hasTheSameAmountOfPaths() = false
    fun pathsHaveSameContent() = false
    fun addMissingPaths() = false
    fun pathDiff() = listOf<Spec.Path>()
    data class Path(val value: String, val methods: List<String>) {
        companion object {
            fun fromRoute(route: Map.Entry<Route?, List<Route>>): Path {
                val methods = mutableListOf<String>()
                val path = route.key.toStr().split("/(authenticate azureAd)", "/(authenticate tokenX)").let { it.first() + it.last() + ":" }
                route.value.forEach { methods.add((it.selector as HttpMethodRouteSelector).method.value.lowercase()) }
                return Path(path, methods)
            }
        }
    }
}
