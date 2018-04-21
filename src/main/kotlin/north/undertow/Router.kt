package north.undertow

import io.undertow.predicate.Predicate
import io.undertow.server.HttpServerExchange
import io.undertow.util.AttachmentKey
import io.undertow.util.HttpString
import io.undertow.util.PathTemplate
import io.undertow.util.StatusCodes
import java.util.concurrent.CompletableFuture

class Router(
        routes: List<Route>,
        private val notFoundRouteHandler: RouteHandler = { exchange ->
            exchange.statusCode = StatusCodes.NOT_FOUND
            exchange.responseSender.send(StatusCodes.NOT_FOUND_STRING)
            Handled
        }
) {
    private val routesByMethod = routes.groupBy(Route::method).mapValues {
        it.value.sortedBy { it.pathTemplate }
    }

    fun apply(exchange: HttpServerExchange): RouteStatus {
        val routes = routesByMethod[exchange.requestMethod]
                ?: return notFoundRouteHandler(exchange)

        val pathParams = mutableMapOf<String, String>()
        for ((_, pathTemplate, predicate, handler) in routes) {
            if (predicate.resolve(exchange) && pathTemplate.matches(exchange.relativePath, pathParams)) {
                val pathTemplateMatch = PathTemplateMatch(pathTemplate.templateString, pathParams)
                exchange.putAttachment(PATH_MATCH_ATTACHMENT_KEY, pathTemplateMatch)
                return handler(exchange)
            }
        }

        return notFoundRouteHandler(exchange)
    }

    companion object {
        val PATH_MATCH_ATTACHMENT_KEY = AttachmentKey.create<PathTemplateMatch>(PathTemplateMatch::class.java)!!
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
 * Only write a response to the exchange if you return [Handled].
 *
 * If you return [AsyncResponse], use [HttpServerExchange.putAttachment]
 * to store your response object on the exchange and use a [ResponseFilter]
 * to serialize your response object and then write it.
 */
typealias RouteHandler = (exchange: HttpServerExchange) -> RouteStatus

sealed class RouteStatus
object Handled : RouteStatus()
class AsyncResponse(val future: CompletableFuture<*>) : RouteStatus()


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
