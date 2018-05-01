package north.undertow.builder

import io.undertow.predicate.Predicate
import io.undertow.predicate.Predicates
import io.undertow.util.StatusCodes
import north.undertow.*

class PipelineHandlerBuilder(private val router: Router) {
    private val requestFilters = mutableListOf<Pair<Predicate, RequestFilter>>()
    private val responseFilters = mutableListOf<Pair<Predicate, ResponseFilter>>()

    private var exceptionHandler: ExceptionHandler = { exception, exchange ->
        println(exception.message)
        exchange.statusCode = StatusCodes.INTERNAL_SERVER_ERROR
        exchange.responseSender.send(StatusCodes.INTERNAL_SERVER_ERROR_STRING)
    }

    fun build(): PipelineHandler = PipelineHandler(
            requestFilters = requestFilters,
            responseFilters = responseFilters,
            router = router,
            exceptionHandler = exceptionHandler
    )

    fun requestFilter(requestFilter: RequestFilter): PipelineHandlerBuilder {
        requestFilters.add(Predicates.truePredicate() to requestFilter)
        return this
    }


    fun requestFilter(predicate: Predicate, requestFilter: RequestFilter): PipelineHandlerBuilder {
        requestFilters.add(predicate to requestFilter)
        return this
    }

    fun responseFilter(responseFilter: ResponseFilter): PipelineHandlerBuilder {
        responseFilters.add(Predicates.truePredicate() to responseFilter)
        return this
    }

    fun responseFilter(predicate: Predicate, responseFilter: ResponseFilter): PipelineHandlerBuilder {
        responseFilters.add(predicate to responseFilter)
        return this
    }

    fun withExceptionHandler(exceptionHandler: ExceptionHandler): PipelineHandlerBuilder {
        this.exceptionHandler = exceptionHandler
        return this
    }
}
