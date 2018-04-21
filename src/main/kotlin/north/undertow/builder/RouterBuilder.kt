package north.undertow.builder

import io.undertow.predicate.Predicate
import io.undertow.util.HttpString
import io.undertow.util.PathTemplate
import io.undertow.util.StatusCodes
import north.undertow.Handled
import north.undertow.Route
import north.undertow.RouteHandler
import north.undertow.Router

class RouterBuilder {
    private val routes = mutableListOf<Route>()
    private var notFoundRouteHandler: RouteHandler = { exchange ->
        exchange.statusCode = StatusCodes.NOT_FOUND
        exchange.responseSender.send(StatusCodes.NOT_FOUND_STRING)
        Handled
    }

    fun build(): Router = Router(routes.toList(), notFoundRouteHandler)

    fun withNotFoundRouteHandler(routeHandler: RouteHandler): RouterBuilder {
        notFoundRouteHandler = routeHandler
        return this
    }

    fun add(method: HttpString, path: String, predicate: Predicate, handler: RouteHandler): RouterBuilder {
        routes.add(Route(method, PathTemplate.create(path), predicate, handler))
        return this
    }
}
