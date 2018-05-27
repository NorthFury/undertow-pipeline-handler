package north.undertow

import io.undertow.predicate.Predicate
import io.undertow.server.HttpServerExchange
import io.undertow.util.AttachmentKey
import io.undertow.util.HttpString
import io.undertow.util.PathTemplate
import java.util.concurrent.CompletableFuture

class Router(
        routes: List<Route>,
        private val notFoundRouteHandler: RouteHandler
) {
    private val routesByMethod = routes.groupBy(Route::method).mapValues {
        it.value.sortedBy { it.pathTemplate }
    }

    fun apply(exchange: HttpServerExchange): RouteStatus {
        val routes = routesByMethod[exchange.requestMethod]
                ?: return notFoundRouteHandler(exchange)

        val matchedRoute = exchange.getAttachment(MATCHED_ROUTE_KEY)
        if (matchedRoute != null) {
            return matchedRoute.handler(exchange)
        }

        val pathParams = mutableMapOf<String, String>()
        for (route in routes) {
            if (route.predicate.resolve(exchange) && route.pathTemplate.matches(exchange.relativePath, pathParams)) {
                val pathTemplateMatch = PathTemplateMatch(route.pathTemplate.templateString, pathParams)
                exchange.putAttachment(PATH_MATCH_ATTACHMENT_KEY, pathTemplateMatch)
                val status = route.handler(exchange)
                if (status == RouteStatus.Dispatched) {
                    exchange.putAttachment(MATCHED_ROUTE_KEY, route)
                }
                return status
            }
        }

        return notFoundRouteHandler(exchange)
    }

    companion object {
        private val MATCHED_ROUTE_KEY = AttachmentKey.create(Route::class.java)!!
        val PATH_MATCH_ATTACHMENT_KEY = AttachmentKey.create(PathTemplateMatch::class.java)!!
    }
}

data class Route(
        val method: HttpString,
        val pathTemplate: PathTemplate,
        val predicate: Predicate,
        val handler: RouteHandler
)

data class PathTemplateMatch(
        val matchedTemplate: String,
        val params: Map<String, String>
)

/**
 * Only write a response to the exchange if you return [RouteStatus.Handled].
 *
 * If you return [RouteStatus.Async], use [HttpServerExchange.putAttachment]
 * to store your response object on the exchange and use a [ResponseFilter]
 * to serialize your response object and then write it.
 */
typealias RouteHandler = (exchange: HttpServerExchange) -> RouteStatus

sealed class RouteStatus {
    object Handled : RouteStatus()
    object Dispatched : RouteStatus()
    class Async(val future: CompletableFuture<*>) : RouteStatus()
}


/**
 * Use this extension method to access the path params defined in the path template.
 *
 * [HttpServerExchange.getPathParameters] will return empty unless some handler adds values to it.
 */
val HttpServerExchange.pathParams
    get(): Map<String, String> = getAttachment(Router.PATH_MATCH_ATTACHMENT_KEY)?.params ?: emptyMap()

/**
 * Will return the path template matched by the [Router] if a match was found otherwise **null**
 */
val HttpServerExchange.matchedPathTemplate
    get(): String? = getAttachment(Router.PATH_MATCH_ATTACHMENT_KEY)?.matchedTemplate
