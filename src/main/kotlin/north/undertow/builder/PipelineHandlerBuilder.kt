package north.undertow.builder

import io.undertow.util.StatusCodes
import north.undertow.*

class PipelineHandlerBuilder(private val router: Router) {
    private val requestFilters = mutableListOf<RequestFilter>()
    private val responseFilters = mutableListOf<ResponseFilter>()

    private var exceptionHandler: ExceptionHandler = { exception, exchange ->
        println(exception.message)
        exchange.statusCode = StatusCodes.INTERNAL_SERVER_ERROR
        exchange.responseSender.send(StatusCodes.INTERNAL_SERVER_ERROR_STRING)
    }

    fun build(): ExpressHandler = ExpressHandler(
            requestFilters = requestFilters,
            responseFilters = responseFilters,
            router = router,
            exceptionHandler = exceptionHandler
    )

    fun before(requestFilter: RequestFilter): PipelineHandlerBuilder {
        requestFilters.add(requestFilter)
        return this
    }

    fun after(responseFilter: ResponseFilter): PipelineHandlerBuilder {
        responseFilters.add(responseFilter)
        return this
    }

    fun withExceptionHandler(exceptionHandler: ExceptionHandler): PipelineHandlerBuilder {
        this.exceptionHandler = exceptionHandler
        return this
    }
}