package north.undertow.it

import io.undertow.Undertow
import io.undertow.predicate.Predicates
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.BlockingHandler
import io.undertow.util.AttachmentKey
import io.undertow.util.Methods
import north.undertow.AsyncProcessStarted
import north.undertow.AsyncResponse
import north.undertow.Continue
import north.undertow.DispatchingHandlerFilter
import north.undertow.builder.PipelineHandlerBuilder
import north.undertow.builder.RouterBuilder
import org.asynchttpclient.Dsl
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CompletableFuture

class IntegrationTests {
    private val asyncHttpClient = Dsl.asyncHttpClient()

    @Test
    fun orderTest() {
        val responseAttachmentKey: AttachmentKey<String> = AttachmentKey.create(String::class.java)

        fun HttpServerExchange.appendToResponse(value: String) {
            val response = getAttachment(responseAttachmentKey)?.plus(" ") ?: ""
            putAttachment(responseAttachmentKey, response + value)
        }

        val router = RouterBuilder()
                .add(Methods.GET, "/test", Predicates.truePredicate()) { exchange ->
                    val future = CompletableFuture.runAsync {
                        Thread.sleep(100)
                        exchange.appendToResponse("asyncHandler")
                    }
                    AsyncResponse(future)
                }
                .build()

        val pipelineHandler = PipelineHandlerBuilder(router)
                .requestFilter { exchange ->
                    exchange.appendToResponse("filter1")
                    Continue
                }
                .requestFilter(DispatchingHandlerFilter.create { next ->
                    BlockingHandler { exchange ->
                        exchange.appendToResponse("dispatched1")
                        next.handleRequest(exchange)
                    }
                })
                .requestFilter { exchange ->
                    exchange.appendToResponse("filter2")
                    Continue
                }
                .requestFilter { exchange ->
                    val future = CompletableFuture.runAsync {
                        Thread.sleep(100)
                        exchange.appendToResponse("asyncFilter")
                    }
                    AsyncProcessStarted(future.thenApply { Continue })
                }
                .requestFilter { exchange ->
                    exchange.appendToResponse("filter3")
                    Continue
                }
                .requestFilter(DispatchingHandlerFilter.create { next ->
                    BlockingHandler { exchange ->
                        exchange.appendToResponse("dispatched2")
                        next.handleRequest(exchange)
                    }
                })
                .requestFilter { exchange ->
                    exchange.appendToResponse("filter4")
                    Continue
                }
                .responseFilter { exchange ->
                    val response = exchange.getAttachment(responseAttachmentKey) ?: ""
                    exchange.responseSender.send(response)
                }
                .build()

        val server = Undertow.builder()
                .addHttpListener(8082, "0.0.0.0", pipelineHandler)
                .build()

        CompletableFuture.runAsync { server.start() }

        val response = asyncHttpClient
                .prepareGet("http://localhost:8082/test")
                .execute().toCompletableFuture().join()

        Assert.assertEquals(
                "filter1 dispatched1 filter2 asyncFilter filter3 dispatched2 filter4 asyncHandler",
                response.responseBody
        )

        server.stop()
    }
}
