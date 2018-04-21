package north.undertow

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.SameThreadExecutor
import java.util.concurrent.CompletableFuture

class ExpressHandler(
        private val requestFilters: List<RequestFilter>,
        private val responseFilters: List<ResponseFilter>,
        private val router: Router,
        private val exceptionHandler: ExceptionHandler
) : HttpHandler {

    override fun handleRequest(exchange: HttpServerExchange) {
        var state = PipelineState.HANDLE_REQUEST_FILTERS
        var i = 0

        fun process() {
            try {
                if (state == PipelineState.HANDLE_REQUEST_FILTERS) {
                    requestFiltersLoop@
                    while (i < requestFilters.size) {
                        val filter = requestFilters[i]
                        val status = filter(exchange)
                        when (status) {
                            Continue -> i++
                            RequestHandled -> {
                                state = PipelineState.HANDLE_RESPONSE_FILTERS
                                break@requestFiltersLoop
                            }
                            is AsyncProcessStarted -> {
                                exchange.dispatch(SameThreadExecutor.INSTANCE, Runnable {
                                    status.future.thenAccept { asyncStatus ->
                                        when (asyncStatus) {
                                            Continue -> i++
                                            RequestHandled -> state = PipelineState.HANDLE_RESPONSE_FILTERS
                                        }
                                        process()
                                    }
                                })
                                return
                            }
                        }
                    }
                    state = PipelineState.HANDLE_ROUTER
                }

                if (state == PipelineState.HANDLE_ROUTER) {
                    val status = router.apply(exchange)
                    if (status is AsyncResponse) {
                        exchange.dispatch(SameThreadExecutor.INSTANCE, Runnable {
                            status.future.thenAccept {
                                state = PipelineState.HANDLE_RESPONSE_FILTERS
                                process()
                            }
                        })
                        return
                    } else {
                        state = PipelineState.HANDLE_RESPONSE_FILTERS
                    }
                }
            } catch (e: Exception) {
                exceptionHandler(e, exchange)
                state = PipelineState.HANDLE_RESPONSE_FILTERS
            }

            if (state == PipelineState.HANDLE_RESPONSE_FILTERS) {
                for (filter in responseFilters) try {
                    filter(exchange)
                } catch (e: Exception) {
                    exceptionHandler(e, exchange)
                }
            }
        }

        process()
    }
}

private enum class PipelineState {
    HANDLE_REQUEST_FILTERS,
    HANDLE_ROUTER,
    HANDLE_RESPONSE_FILTERS
}

sealed class FilterStatus
sealed class SyncFilterStatus : FilterStatus()
object Continue : SyncFilterStatus()
object RequestHandled : SyncFilterStatus()
class AsyncProcessStarted(val future: CompletableFuture<SyncFilterStatus>) : FilterStatus()

typealias RequestFilter = (exchange: HttpServerExchange) -> FilterStatus

/**
 * Implementations should be non blocking.
 *
 * Do not use the exchange in async code ran from a [ResponseFilter].
 */
typealias ResponseFilter = (exchange: HttpServerExchange) -> Unit

typealias ExceptionHandler = (exception: Exception, exchange: HttpServerExchange) -> Unit
