package north.undertow

import io.undertow.server.HttpHandler
import io.undertow.util.AttachmentKey

object DispatchingHandler {
    fun createRequestFilter(builder: (next: HttpHandler) -> HttpHandler): RequestFilter {
        val handler = builder(rootHandlerDispatcher)

        return { exchange ->
            handler.handleRequest(exchange)
            FilterStatus.Dispatched
        }
    }

    fun createRouteHandler(dispatchingHandlerBuilder: (next: HttpHandler) -> HttpHandler, next: RouteHandler): RouteHandler {
        val dispatchedKey = AttachmentKey.create(Boolean::class.java)
        val handler = dispatchingHandlerBuilder(rootHandlerDispatcher)

        return { exchange ->
            val dispatched = exchange.getAttachment(dispatchedKey) ?: false
            if (!dispatched) {
                exchange.putAttachment(dispatchedKey, true)
                handler.handleRequest(exchange)
                RouteStatus.Dispatched
            } else {
                next(exchange)
            }

        }
    }

    private val rootHandlerDispatcher = HttpHandler { exchange ->
        val rootHandler = exchange.getAttachment(PipelineHandler.ROOT_HANDLER_KEY)
        exchange.dispatch(rootHandler)
    }
}