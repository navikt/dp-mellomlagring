package no.nav.dagpenger.mellomlagring.openapi

import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.mockk.InternalPlatformDsl.toStr

internal class ApplicationSpec(private val paths: List<Path.ApplicationPath>) {
    companion object {
        internal fun pathFromRoute(route: Map.Entry<Route?, List<Route>>): Pair<String, MutableList<Method>> {
            val methods = mutableListOf<Method>()
            val path = route.key
                .toStr()
                .split("/(authenticate azureAd)", "/(authenticate tokenX)")
                .let { it.first() + it.last() }
            route.value.forEach { methods.add(Method((it.selector as HttpMethodRouteSelector).method.value.lowercase())) }
            return Pair(path, methods)
        }
    }
    fun missingPaths(other: List<Path>) = this.paths.filterNot { other.contains(it) }
    fun superfluousPaths(other: List<Path>): List<Path> = other.filterNot { this.paths.contains(it) }
}

internal class OpenApiSpec(var paths: List<Path>, private val serDer: OpenApiSerDer) {
    companion object {
        fun fromJson(openapiFilePath: String): OpenApiSpec {
            return OpenApiSerDer.fromFile(openapiFilePath).let {
                OpenApiSpec(
                    paths = it.paths.map { path ->
                        Path.OpenApiSpecPath(
                            value = path.key,
                            methods = path.value.map { method -> Method(method.key) }
                        )
                    },
                    serDer = it
                )
            }
        }
    }

    infix fun `should contain the same paths as`(application: ApplicationSpec) {
        val pathAssertionError = PathAssertionError()
        application.missingPaths(this.paths).let {
            pathAssertionError.addMissingPaths(it)
        }
        application.superfluousPaths(this.paths).let {
            pathAssertionError.addsuperfluousPaths(it)
        }
        pathAssertionError.evaluate()
    }

    infix fun `paths should have the same methods as`(application: ApplicationSpec) {
    }

    internal fun updatePaths(application: ApplicationSpec) {
        val pathsToBeAdded = application.missingPaths(this.paths)
        val pathsToBeRemoved = application.superfluousPaths(this.paths)
        this.paths = this.paths - pathsToBeRemoved + pathsToBeAdded
    }

    internal fun toJson(): ByteArray = serDer.generateNewSpecFile(this).toByteArray()
}

abstract class Path(val value: String, val methods: List<Method>) {
    override fun equals(other: Any?): Boolean {
        require(other is Path)
        return this.value.compareTo(other.value) == 0
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + methods.hashCode()
        return result
    }

    internal class ApplicationPath(value: String, methods: List<Method>) : Path(value, methods)
    internal class OpenApiSpecPath(value: String, methods: List<Method>) : Path(value, methods)
}
data class Method(val operation: String)

internal fun Routing.routesInApplication(): ApplicationSpec {
    val newPaths = mutableListOf<Path.ApplicationPath>()
    allRoutes(this).filter { it.selector is HttpMethodRouteSelector }
        .groupBy { it.parent }
        .forEach { route ->
            val (path, methods) = ApplicationSpec.pathFromRoute(route)
            newPaths.add(Path.ApplicationPath(path, methods))
        }
    return ApplicationSpec(newPaths)
}

private fun allRoutes(root: Route): List<Route> = listOf(root) + root.children.flatMap { allRoutes(it) }
