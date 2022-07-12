package no.nav.dagpenger.mellomlagring.openapi

import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.mockk.InternalPlatformDsl.toStr
import no.nav.security.mock.oauth2.http.objectMapper
import java.io.File

internal fun Routing.routesInApplication(): Spec {
    val newPaths = mutableListOf<Spec.Path>()
    allRoutes(this).filter { it.selector is HttpMethodRouteSelector }
        .groupBy { it.parent }
        .forEach { route ->
            Spec.Path.fromRoute(route)
            val methods = mutableListOf<String>()
            val path = route.key
                .toStr()
                .split("/(authenticate azureAd)", "/(authenticate tokenX)")
                .let { it.first() + it.last() + ":" }
            route.value.forEach { methods.add((it.selector as HttpMethodRouteSelector).method.value.lowercase()) }
            newPaths.add(Spec.Path(path, methods))
        }
    return Spec(newPaths)
}

private fun allRoutes(root: Route): List<Route> {
    return listOf(root) + root.children.flatMap { allRoutes(it) }
}

internal data class Spec(val paths: List<Path>) {

    infix fun `should have same number of paths as`(application: Spec) {
        if (this.paths.size != application.paths.size)
            throw AssertionError("Expected spec to contain ${application.paths.size} paths, actual number of paths was ${this.paths.size}")
    }

    companion object {
        fun fromJson(openapiFilePath: String): Spec {
            val paths = mutableListOf<Path>()
            objectMapper.readTree(File(openapiFilePath)).let {
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

    data class Path(val value: String, val methods: List<String>) {
        companion object {
            fun fromRoute(route: Map.Entry<Route?, List<Route>>): Path {
                val methods = mutableListOf<String>()
                val path = route.key.toStr().split("/(authenticate azureAd)", "/(authenticate tokenX)")
                    .let { it.first() + it.last() + ":" }
                route.value.forEach { methods.add((it.selector as HttpMethodRouteSelector).method.value.lowercase()) }
                return Path(path, methods)
            }
        }
    }
}
