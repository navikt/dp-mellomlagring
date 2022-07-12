package no.nav.dagpenger.mellomlagring.openapi

import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.mockk.InternalPlatformDsl.toStr
import no.nav.dagpenger.mellomlagring.openapi.ApplicationSpec.Companion.pathfromRoute
import no.nav.security.mock.oauth2.http.objectMapper
import java.io.File

internal fun Routing.routesInApplication(): ApplicationSpec {
    val newPaths = mutableListOf<Path>()
    allRoutes(this).filter { it.selector is HttpMethodRouteSelector }
        .groupBy { it.parent }
        .forEach { route ->
            pathfromRoute(route)
            val methods = mutableListOf<String>()
            val path = route.key
                .toStr()
                .split("/(authenticate azureAd)", "/(authenticate tokenX)")
                .let { it.first() + it.last() }
            route.value.forEach { methods.add((it.selector as HttpMethodRouteSelector).method.value.lowercase()) }
            newPaths.add(Path(path, methods))
        }
    return ApplicationSpec(newPaths)
}

private fun allRoutes(root: Route): List<Route> {
    return listOf(root) + root.children.flatMap { allRoutes(it) }
}

data class Path(val value: String, val methods: List<String>) {
    override fun equals(other: Any?): Boolean {
        require(other is Path)
        return this.value.compareTo(other.value) == 0
    }
}

internal class ApplicationSpec(val paths: List<Path>) {
    companion object {
        fun pathfromRoute(route: Map.Entry<Route?, List<Route>>): Path {
            val methods = mutableListOf<String>()
            val path = route.key.toStr().split("/(authenticate azureAd)", "/(authenticate tokenX)")
                .let { it.first() + it.last() + ":" }
            route.value.forEach { methods.add((it.selector as HttpMethodRouteSelector).method.value.lowercase()) }
            return Path(path, methods)
        }
    }
}

internal class OpenApiSpec(var paths: List<Path>) {
    infix fun `should have same number of paths as`(application: ApplicationSpec) {
        if (this.paths.size != application.paths.size)
            throw PathAssertionError("Expected openapi spec to contain ${application.paths.size} paths, actual number of paths was ${this.paths.size}")
    }

    internal fun addMissingRoutes(application: ApplicationSpec) {
        val pathsToBeAdded = application.paths.filterNot { !this.paths.contains(it) }
        val pathsToBeRemoved = paths.filterNot { !application.paths.contains(it) }
        this.paths = this.paths - pathsToBeRemoved + pathsToBeAdded
    }

    companion object {
        fun fromJson(openapiFilePath: String): OpenApiSpec {
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
            return OpenApiSpec(paths)
        }
    }
}

internal class AssertionSpecRecovery(
    val openApiSpec: OpenApiSpec,
    val application: ApplicationSpec,
    private val recoveryFilePath: String? = null
) {
    private var pathAssertionRecovered = false
    private var methodAssertRecovered = false
    fun pathAssertionError() {
        openApiSpec.addMissingRoutes(application)
    }

    fun writeToFile() {
        if (pathAssertionRecovered && methodAssertRecovered) {
            println("skriv til fil ${recoveryFilePath ?: "default"}")
        }
    }
}

internal class PathAssertionError(message: String) : AssertionError(message)
