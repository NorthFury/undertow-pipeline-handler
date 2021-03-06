package north.undertow

import io.undertow.predicate.Predicate
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.AttachmentKey
import io.undertow.util.SameThreadExecutor
import java.util.concurrent.CompletableFuture

class PipelineHandler(
        private val requestFilters: List<Pair<Predicate, RequestFilter>>,
        private val responseFilters: List<Pair<Predicate, ResponseFilter>>,
        private val router: Router,
        private val exceptionHandler: ExceptionHandler
) : HttpHandler {

    override fun handleRequest(exchange: HttpServerExchange) {
        exchange.putAttachment(ROOT_HANDLER_KEY, this)

        var state = exchange.getAttachment(PIPELINE_STATE_KEY) ?: PipelineState.HANDLE_REQUEST_FILTERS

        try {
            if (state == PipelineState.HANDLE_REQUEST_FILTERS) {
                var i = exchange.getAttachment(FILTER_POSITION_KEY) ?: 0
                requestFiltersLoop@
                while (i < requestFilters.size) {
                    val (predicate, filter) = requestFilters[i]
                    if (!predicate.resolve(exchange)) {
                        i++
                        continue
                    }

                    val status = filter(exchange)
                    when (status) {
                        FilterStatus.Done.Continue -> i++
                        FilterStatus.Done.RequestHandled -> {
                            state = PipelineState.HANDLE_RESPONSE_FILTERS
                            break@requestFiltersLoop
                        }
                        FilterStatus.Dispatched -> {
                            exchange.putAttachment(FILTER_POSITION_KEY, i + 1)
                            return
                        }
                        is FilterStatus.AsyncStarted -> {
                            exchange.dispatch(SameThreadExecutor.INSTANCE, Runnable {
                                status.future.thenAccept { asyncStatus ->
                                    when (asyncStatus) {
                                        FilterStatus.Done.Continue ->
                                            exchange.putAttachment(FILTER_POSITION_KEY, i + 1)
                                        FilterStatus.Done.RequestHandled ->
                                            exchange.putAttachment(
                                                    PIPELINE_STATE_KEY,
                                                    PipelineState.HANDLE_RESPONSE_FILTERS
                                            )
                                    }
                                    handleRequest(exchange)
                                }
                            })
                            return
                        }
                    }
                }
                if (state == PipelineState.HANDLE_REQUEST_FILTERS) {
                    state = PipelineState.HANDLE_ROUTER
                }
            }

            if (state == PipelineState.HANDLE_ROUTER) {
                val status = router.apply(exchange)
                when (status) {
                    RouteStatus.Dispatched -> {
                        exchange.putAttachment(PIPELINE_STATE_KEY, PipelineState.HANDLE_ROUTER)
                        return
                    }
                    is RouteStatus.Async -> {
                        exchange.dispatch(SameThreadExecutor.INSTANCE, Runnable {
                            status.future.thenAccept {
                                exchange.putAttachment(PIPELINE_STATE_KEY, PipelineState.HANDLE_RESPONSE_FILTERS)
                                handleRequest(exchange)
                            }
                        })
                        return
                    }
                    else -> state = PipelineState.HANDLE_RESPONSE_FILTERS
                }

            }
        } catch (e: Exception) {
            exceptionHandler(e, exchange)
            state = PipelineState.HANDLE_RESPONSE_FILTERS
        }

        if (state == PipelineState.HANDLE_RESPONSE_FILTERS) {
            for ((predicate, filter) in responseFilters) try {
                if (predicate.resolve(exchange)) filter(exchange)
            } catch (e: Exception) {
                exceptionHandler(e, exchange)
            }
        }
    }

    companion object {
        private val PIPELINE_STATE_KEY = AttachmentKey.create(PipelineState::class.java)!!
        private val FILTER_POSITION_KEY = AttachmentKey.create(Int::class.java)!!
        val ROOT_HANDLER_KEY = AttachmentKey.create(HttpHandler::class.java)!!
    }
}

private enum class PipelineState {
    HANDLE_REQUEST_FILTERS,
    HANDLE_ROUTER,
    HANDLE_RESPONSE_FILTERS
}

sealed class FilterStatus {
    sealed class Done : FilterStatus() {
        object Continue : Done()
        object RequestHandled : Done()
    }
    object Dispatched : FilterStatus()
    class AsyncStarted(val future: CompletableFuture<out Done>) : FilterStatus()
}

typealias RequestFilter = (exchange: HttpServerExchange) -> FilterStatus

/**
 * Implementations should be non blocking.
 *
 * Do not use the exchange in async code ran from a [ResponseFilter].
 */
typealias ResponseFilter = (exchange: HttpServerExchange) -> Unit

typealias ExceptionHandler = (exception: Exception, exchange: HttpServerExchange) -> Unit
