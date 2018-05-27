package north.undertow

import io.undertow.server.HttpHandler

object DispatchingHandlerFilter {
    fun create(builder: (next: HttpHandler) -> HttpHandler): RequestFilter {
        val handler = builder(next)

        return { exchange ->
            handler.handleRequest(exchange)
            Dispatched
        }
    }

    val next = HttpHandler { exchange ->
        val rootHandler = exchange.getAttachment(PipelineHandler.ROOT_HANDLER_KEY)
        exchange.dispatch(rootHandler)
    }
}
