package north.undertow.builder

import io.undertow.predicate.Predicate
import io.undertow.predicate.Predicates
import io.undertow.util.StatusCodes
import north.undertow.*
import org.slf4j.LoggerFactory

class PipelineHandlerBuilder(private val router: Router) {
    private val requestFilters = mutableListOf<Pair<Predicate, RequestFilter>>()
    private val responseFilters = mutableListOf<Pair<Predicate, ResponseFilter>>()

    private var exceptionHandler: ExceptionHandler = { exception, exchange ->
        logger.error("pipeline handler exception", exception)
        if (!exchange.isResponseStarted) {
            exchange.statusCode = StatusCodes.INTERNAL_SERVER_ERROR
            exchange.endExchange()
        }
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

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineHandlerBuilder::class.java)!!
    }
}
